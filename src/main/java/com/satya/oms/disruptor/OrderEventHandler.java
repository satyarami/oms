package com.satya.oms.disruptor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.lmax.disruptor.EventHandler;
import com.satya.oms.config.OMSConfig;
import com.satya.oms.core.OMSOrderBook;
import com.satya.oms.gateway.GatewayListener;
import com.satya.oms.gateway.MarketGateway;
import com.satya.oms.sbe.*;

import io.aeron.Aeron;
import io.aeron.Publication;

/**
 * Disruptor consumer that validates orders, updates the order book,
 * encodes an SBE {@code OrderRequest} to the {@link MarketGateway},
 * and reacts to SBE-encoded gateway callbacks (Ack / Reject / Fill / Done).
 * <p>
 * On {@code onDone} or {@code onReject} the handler publishes the final
 * {@code Order} message (with fills) to the Aeron outbound stream.
 */
public class OrderEventHandler implements EventHandler<OrderEvent>, GatewayListener {

    private static final String CHANNEL = OMSConfig.getAeronChannel();
    private static final int STREAM_ID = OMSConfig.getAeronOutStreamId();

    private final OMSOrderBook orderBook = new OMSOrderBook();
    private final MarketGateway gateway = new MarketGateway();

    // ── Aeron publication ──────────────────────────────────────────────────
    private final Publication publication;

    // ── SBE encoders / decoders ────────────────────────────────────────────
    // OrderRequest encoding (handler → gateway)
    private final UnsafeBuffer requestBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(128));
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderRequestEncoder requestEncoder = new OrderRequestEncoder();

    // Gateway response decoding
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AckEventDecoder ackDecoder = new AckEventDecoder();
    private final RejectEventDecoder rejectDecoder = new RejectEventDecoder();
    private final FillEventDecoder fillDecoder = new FillEventDecoder();
    private final DoneEventDecoder doneDecoder = new DoneEventDecoder();

    // Outbound Order encoding (to Aeron)
    private final UnsafeBuffer publishBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));
    private final OrderEncoder orderEncoder = new OrderEncoder();

    // ── Per-order fill tracking ────────────────────────────────────────────
    /** Accumulates fill state for each in-flight order. */
    private static class OrderFillState {
        final long orderId;
        final long symbolId;
        final byte side;
        final long originalQty;
        final long price;
        long filledQty;
        final List<long[]> fills = new ArrayList<>();   // {fillQty, fillPrice, execId}

        OrderFillState(long orderId, long symbolId, byte side, long originalQty, long price) {
            this.orderId = orderId;
            this.symbolId = symbolId;
            this.side = side;
            this.originalQty = originalQty;
            this.price = price;
        }
    }

    private final ConcurrentMap<Long, OrderFillState> inflightOrders = new ConcurrentHashMap<>();

    // ────────────────────────────────────────────────────────────────────────
    public OrderEventHandler() {
        Aeron.Context ctx = new Aeron.Context();
        Aeron aeron = Aeron.connect(ctx);
        publication = aeron.addPublication(CHANNEL, STREAM_ID);
        System.out.println("OrderEventHandler connected to Aeron Media Driver.");
    }

    // ====================================================================
    // Disruptor onEvent
    // ====================================================================
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            // ── Validation ─────────────────────────────────────────────────
            if (event.getQuantity() <= 0 || event.getPrice() <= 0) {
                System.out.println("Rejected invalid order: " + event.getOrderId());
                publishRejected(event);
                return;
            }
            if (event.getQuantity() > OMSConfig.getMaxOrderQuantity()) {
                System.out.println("Rejected order exceeding max qty: " + event.getOrderId());
                publishRejected(event);
                return;
            }

            // ── Order book ─────────────────────────────────────────────────
            orderBook.addOrder(event);
            orderBook.matchOrders();

            // ── Register in-flight state ───────────────────────────────────
            inflightOrders.put(event.getOrderId(),
                    new OrderFillState(
                            event.getOrderId(),
                            event.getSymbolId(),
                            event.getSide(),
                            event.getQuantity(),
                            event.getPrice()));

            // ── Encode OrderRequest and send to gateway ────────────────────
            requestEncoder.wrapAndApplyHeader(requestBuffer, 0, headerEncoder)
                    .orderId(event.getOrderId())
                    .symbolId((int) event.getSymbolId())
                    .side(event.getSideEnum())
                    .quantity(event.getQuantity())
                    .price(event.getPrice());
            int reqLen = MessageHeaderEncoder.ENCODED_LENGTH + requestEncoder.encodedLength();

            gateway.sendOrder(requestBuffer, 0, reqLen, this);

        } catch (Exception e) {
            System.err.println("Exception in onEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================================================================
    // GatewayListener callbacks (SBE buffers in, no domain objects)
    // ====================================================================

    @Override
    public void onAck(DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        ackDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());
        System.out.printf("Handler received ACK for order ID=%d%n", ackDecoder.orderId());
    }

    @Override
    public void onReject(DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        rejectDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        long orderId = rejectDecoder.orderId();
        int rejectCode = (int) rejectDecoder.rejectCode();
        System.out.printf("Handler received REJECT for order ID=%d code=%d%n", orderId, rejectCode);

        OrderFillState state = inflightOrders.remove(orderId);
        if (state != null) {
            publishFinalOrder(state, OrderState.REJECTED);
        }
    }

    @Override
    public void onFill(DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        fillDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        long orderId   = fillDecoder.orderId();
        long fillQty   = fillDecoder.fillQty();
        long fillPrice = fillDecoder.fillPrice();
        long execId    = fillDecoder.executionId();

        OrderFillState state = inflightOrders.get(orderId);
        if (state != null) {
            state.filledQty += fillQty;
            state.fills.add(new long[]{fillQty, fillPrice, execId});
            System.out.printf("Handler received FILL for order ID=%d fillQty=%d totalFilled=%d/%d execId=%d%n",
                    orderId, fillQty, state.filledQty, state.originalQty, execId);
        }
    }

    @Override
    public void onDone(DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        doneDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        long orderId = doneDecoder.orderId();
        System.out.printf("Handler received DONE for order ID=%d%n", orderId);

        OrderFillState state = inflightOrders.remove(orderId);
        if (state != null) {
            OrderState finalState = (state.filledQty >= state.originalQty)
                    ? OrderState.FILLED
                    : OrderState.PARTIALLY_FILLED;
            publishFinalOrder(state, finalState);
        }
    }

    // ====================================================================
    // Aeron publication helpers
    // ====================================================================

    /** Publish a REJECTED order (validation / risk failure – no gateway involvement). */
    private void publishRejected(OrderEvent event) {
        orderEncoder.wrapAndApplyHeader(publishBuffer, 0, headerEncoder)
                .orderId(event.getOrderId())
                .symbolId((int) event.getSymbolId())
                .side(event.getSideEnum())
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .state(OrderState.REJECTED)
                .filledQty(0)
                .remainingQty(event.getQuantity());
        orderEncoder.fillsCount(0);

        int len = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();
        offerToAeron(publishBuffer, len, event.getOrderId());
    }

    /** Publish the final {@code Order} message with accumulated fills. */
    private void publishFinalOrder(OrderFillState state, OrderState orderState) {
        long remaining = state.originalQty - state.filledQty;
        if (remaining < 0) remaining = 0;

        System.out.printf("Publishing order: ID=%d state=%s filledQty=%d remainingQty=%d fills=%d%n",
                state.orderId, orderState, state.filledQty, remaining, state.fills.size());

        orderEncoder.wrapAndApplyHeader(publishBuffer, 0, headerEncoder)
                .orderId(state.orderId)
                .symbolId((int) state.symbolId)
                .side(Side.get(state.side))
                .quantity(state.originalQty)
                .price(state.price)
                .state(orderState)
                .filledQty(state.filledQty)
                .remainingQty(remaining);

        OrderEncoder.FillsEncoder fillsEncoder = orderEncoder.fillsCount(state.fills.size());
        for (long[] f : state.fills) {
            fillsEncoder.next()
                    .fillQty(f[0])
                    .fillPrice(f[1])
                    .executionId(f[2]);
            System.out.printf("  Fill: qty=%d price=%d execId=%d%n", f[0], f[1], f[2]);
        }

        int len = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();
        offerToAeron(publishBuffer, len, state.orderId);
    }

    /** Blocking offer to the Aeron publication with back-pressure retry. */
    private void offerToAeron(UnsafeBuffer buffer, int length, long orderId) {
        while (true) {
            long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                System.out.println("Order published successfully! ID=" + orderId);
                break;
            } else {
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
}
