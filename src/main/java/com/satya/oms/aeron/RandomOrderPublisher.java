package com.satya.oms.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.Side;
import com.satya.oms.sbe.OrderState;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class RandomOrderPublisher {

    private static final String CHANNEL = "aeron:ipc?term-length=64k";
    private static final int STREAM_ID = 1001;

    public static void main(String[] args) throws InterruptedException {
        // Run for this many milliseconds (10 seconds default)
        long runDurationMs = 10_000;
        if (args.length > 0) {
            runDurationMs = Long.parseLong(args[0]);
        }

        Aeron.Context ctx = new Aeron.Context();

        try (Aeron aeron = Aeron.connect(ctx);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

            System.out.println("Random Order Publisher connected to Aeron Media Driver.");

            // Create buffer and encoders (reuse for all orders)
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(512);
            final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
            final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
            final OrderEncoder orderEncoder = new OrderEncoder();

            long endTime = System.currentTimeMillis() + runDurationMs;

            while (System.currentTimeMillis() < endTime) {
                // Create a random order
                long orderId = ThreadLocalRandom.current().nextLong(1, 1_000_000);
                int symbolId = ThreadLocalRandom.current().nextInt(1000, 2000);
                Side side = ThreadLocalRandom.current().nextBoolean() ? Side.BUY : Side.SELL;
                long quantity = ThreadLocalRandom.current().nextLong(1, 1000);
                long price = ThreadLocalRandom.current().nextLong(1000, 2000);

                orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                    .orderId(orderId)
                    .symbolId(symbolId)
                    .side(side)
                    .quantity(quantity)
                    .price(price)
                    .state(OrderState.NEW)
                    .filledQty(0)
                    .remainingQty(quantity);

                int encodedLength = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();

                // Try publishing until successful
                while (true) {
                    long result = publication.offer(buffer, 0, encodedLength);
                    if (result > 0) {
                        System.out.println("Order sent: id=" + orderId +
                                " symbol=" + symbolId +
                                " side=" + side +
                                " qty=" + quantity +
                                " price=" + price);
                        break;
                    } else {
                        Thread.sleep(1); // backoff if not accepted
                    }
                }

                // Random sleep between 1–10 milliseconds
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
            }

            System.out.println("Random Order Publisher finished sending orders.");
        }
    }
}
