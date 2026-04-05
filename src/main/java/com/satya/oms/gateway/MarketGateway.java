package com.satya.oms.gateway;

import com.satya.oms.config.OMSConfig;
import com.satya.oms.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

/**
 * Simulated market gateway.
 * <p>
 * Receives an SBE-encoded {@code OrderRequest}, replies with SBE-encoded
 * {@code AckEvent}/{@code RejectEvent}, then asynchronously sends
 * {@code FillEvent}s and a final {@code DoneEvent} over a configurable interval.
 * <p>
 * All communication with the caller happens through {@link GatewayListener}
 * callbacks carrying raw SBE buffers – no Java domain objects cross the boundary.
 */
public class MarketGateway {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gateway-fill-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final long fillIntervalMs = OMSConfig.getGatewayFillIntervalMs();

    // ── SBE decoders / encoders (reused – single-threaded access per call) ──
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderRequestDecoder requestDecoder = new OrderRequestDecoder();

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AckEventEncoder ackEncoder = new AckEventEncoder();
    private final RejectEventEncoder rejectEncoder = new RejectEventEncoder();

    // Scratch buffers for encoding outbound messages
    private final UnsafeBuffer ackBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(128));
    private final UnsafeBuffer rejectBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(128));

    private long executionIdCounter = System.currentTimeMillis() * 1000;

    /**
     * Accept an SBE-encoded {@code OrderRequest} and begin the simulated
     * exchange lifecycle.
     *
     * @param buffer   buffer containing the {@code OrderRequest} (with SBE message header)
     * @param offset   start offset of the message header in the buffer
     * @param length   total length of the encoded message (header + body)
     * @param listener callback that receives SBE-encoded gateway responses
     */
    public void sendOrder(DirectBuffer buffer, int offset, int length, GatewayListener listener) {
        // ── Decode the OrderRequest ────────────────────────────────────────
        headerDecoder.wrap(buffer, offset);
        requestDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        long orderId  = requestDecoder.orderId();
        int  symbolId = (int) requestDecoder.symbolId();
        byte side     = (byte) requestDecoder.side().value();
        long quantity = requestDecoder.quantity();
        long price    = requestDecoder.price();

        System.out.printf("Gateway received OrderRequest: ID=%d symbol=%d side=%d qty=%d price=%d%n",
                orderId, symbolId, side, quantity, price);

        // ── Decide: 90 % ack, 10 % reject ─────────────────────────────────
        int action = ThreadLocalRandom.current().nextInt(100);
        if (action < 10) {
            // REJECT
            rejectEncoder.wrapAndApplyHeader(rejectBuffer, 0, headerEncoder)
                    .orderId(orderId)
                    .rejectCode(1);  // generic reject code
            int msgLen = MessageHeaderEncoder.ENCODED_LENGTH + rejectEncoder.encodedLength();
            System.out.printf("Gateway REJECTED order ID=%d%n", orderId);
            listener.onReject(rejectBuffer, 0, msgLen);
            return;
        }

        // ACK
        ackEncoder.wrapAndApplyHeader(ackBuffer, 0, headerEncoder)
                .orderId(orderId);
        int ackLen = MessageHeaderEncoder.ENCODED_LENGTH + ackEncoder.encodedLength();
        System.out.printf("Gateway ACK order ID=%d%n", orderId);
        listener.onAck(ackBuffer, 0, ackLen);

        // ── Schedule asynchronous fills ────────────────────────────────────
        scheduleFills(orderId, quantity, price, listener);
    }

    /**
     * Generates random fills over time until the full quantity is consumed,
     * then sends a {@code DoneEvent}.
     */
    private void scheduleFills(long orderId, long totalQty, long price, GatewayListener listener) {
        // Per-order state tracked by the scheduled task
        final long[] remaining = {totalQty};

        // Each fill gets its own buffer (may be delivered on the scheduler thread)
        final UnsafeBuffer fillBuf = new UnsafeBuffer(ByteBuffer.allocateDirect(128));
        final UnsafeBuffer doneBuf = new UnsafeBuffer(ByteBuffer.allocateDirect(64));
        final FillEventEncoder localFillEncoder = new FillEventEncoder();
        final DoneEventEncoder localDoneEncoder = new DoneEventEncoder();
        final MessageHeaderEncoder localHeaderEncoder = new MessageHeaderEncoder();

        ScheduledFuture<?>[] handle = new ScheduledFuture<?>[1];
        handle[0] = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (remaining[0] <= 0) {
                    handle[0].cancel(false);
                    return;
                }

                // Random fill: between 1 and remaining (at least 1)
                long fillQty;
                if (remaining[0] == 1) {
                    fillQty = 1;
                } else {
                    fillQty = 1 + ThreadLocalRandom.current().nextLong(remaining[0]);
                }
                remaining[0] -= fillQty;
                long execId = executionIdCounter++;

                // Encode FillEvent
                localFillEncoder.wrapAndApplyHeader(fillBuf, 0, localHeaderEncoder)
                        .orderId(orderId)
                        .fillQty(fillQty)
                        .fillPrice(price)
                        .executionId(execId);
                int fillLen = MessageHeaderEncoder.ENCODED_LENGTH + localFillEncoder.encodedLength();

                System.out.printf("Gateway FILL order ID=%d fillQty=%d remaining=%d execId=%d%n",
                        orderId, fillQty, remaining[0], execId);
                listener.onFill(fillBuf, 0, fillLen);

                // If fully filled, send DoneEvent and cancel schedule
                if (remaining[0] <= 0) {
                    localDoneEncoder.wrapAndApplyHeader(doneBuf, 0, localHeaderEncoder)
                            .orderId(orderId);
                    int doneLen = MessageHeaderEncoder.ENCODED_LENGTH + localDoneEncoder.encodedLength();
                    System.out.printf("Gateway DONE order ID=%d%n", orderId);
                    listener.onDone(doneBuf, 0, doneLen);
                    handle[0].cancel(false);
                }
            } catch (Exception e) {
                System.err.println("Error in fill scheduler for order " + orderId + ": " + e.getMessage());
                e.printStackTrace();
                handle[0].cancel(false);
            }
        }, fillIntervalMs, fillIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Shuts down the fill scheduler (call on application shutdown). */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}