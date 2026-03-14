package com.satya.oms.disruptor;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.lmax.disruptor.EventHandler;
import com.satya.oms.core.OMSOrderBook;
import com.satya.oms.gateway.MarketGateway;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.OrderState;
import com.satya.oms.sbe.Side;
import io.aeron.Aeron;
import io.aeron.Publication;

public class OrderEventHandler implements EventHandler<OrderEvent> {

    private static final String CHANNEL = "aeron:ipc?term-length=64k";
    private static final int STREAM_ID = 1002;

    private final OMSOrderBook orderBook = new OMSOrderBook();
    private final MarketGateway gateway = new MarketGateway();
    final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(256);
    final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final OrderEncoder orderEncoder = new OrderEncoder();
    private final Publication publication;

    public OrderEventHandler() {
        Aeron.Context ctx = new Aeron.Context();
        Aeron aeron = Aeron.connect(ctx);
        publication = aeron.addPublication(CHANNEL, STREAM_ID);
        System.out.println("OrderEventHandler connected to Aeron Media Driver.");
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            if (event.getQuantity() <= 0 || event.getPrice() <= 0) {
                System.out.println("Rejected invalid order: " + event.getOrderId());
                event.setBufferState((byte) OrderState.REJECTED.value());
                publish(event);
                return;
            }
            if (event.getQuantity() > 1000) {
                System.out.println("Rejected order exceeding max qty: " + event.getOrderId());
                event.setBufferState((byte) OrderState.REJECTED.value());
                publish(event);
                return;
            }
            orderBook.addOrder(event);
            orderBook.matchOrders();
            gateway.sendOrder(event);
          
            publish(event);
        } catch (Exception e) {
            System.err.println("Exception in onEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void publish(OrderEvent event) {
    	System.out.println("Publishing order: ID=" + event.getOrderId() + " state=" + event.getStateEnum());
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .orderId(event.getOrderId())
                .symbolId(event.getSymbolId())
                .side(Side.get(event.getSide()))
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .state(event.getStateEnum())
                .filledQty(event.getFilledQty())
                .remainingQty(event.getRemainingQty());
        int length = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();
        while (true) {
            long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                System.out.println("Order published successfully! ID=" + event.getOrderId());
                break;
            } else {
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
}