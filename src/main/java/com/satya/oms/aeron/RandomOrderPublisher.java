package com.satya.oms.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.DirectBuffer;
import com.satya.oms.model.Order;
import com.satya.oms.model.OrderState;

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

            long endTime = System.currentTimeMillis() + runDurationMs;

            while (System.currentTimeMillis() < endTime) {
                // Create a random order
                Order order = new Order();
                order.setOrderId(ThreadLocalRandom.current().nextLong(1, 1_000_000));
                order.setSymbolId(ThreadLocalRandom.current().nextInt(1000, 2000));
                order.setSide(ThreadLocalRandom.current().nextBoolean() ? (byte)0 : (byte)1); // 0=Buy,1=Sell
                order.setQuantity(ThreadLocalRandom.current().nextLong(1, 1000));
                order.setPrice(ThreadLocalRandom.current().nextLong(1000, 2000));
                order.setState(OrderState.NEW);

                DirectBuffer buffer = order.getBuffer();

                // Try publishing until successful
                while (true) {
                    long result = publication.offer(buffer, 0, Order.SIZE);
                    if (result > 0) {
                        System.out.println("Order sent: id=" + order.getOrderId() +
                                " symbol=" + order.getSymbolId() +
                                " side=" + order.getSide() +
                                " qty=" + order.getQuantity() +
                                " price=" + order.getPrice());
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
