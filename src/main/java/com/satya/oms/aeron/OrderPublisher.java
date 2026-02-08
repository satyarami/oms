package com.satya.oms.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.DirectBuffer;
import com.satya.oms.model.Order;
import com.satya.oms.model.OrderState;

import java.util.concurrent.ThreadLocalRandom;

public class OrderPublisher {

    private static final String CHANNEL = "aeron:ipc?term-length=64k";
    private static final int STREAM_ID = 1001;


    public static void main(String[] args) throws InterruptedException {
        Aeron.Context ctx = new Aeron.Context();

        try (Aeron aeron = Aeron.connect(ctx);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

            System.out.println("Publisher connected to Aeron Media Driver.");

            // Create an order
            Order order = new Order();
            order.setOrderId(ThreadLocalRandom.current().nextLong(1, 1_000_000));
            order.setSymbolId(1001);
            order.setSide((byte) 0); // 0 = Buy, 1 = Sell
            order.setQuantity(500);
            order.setPrice(12345);   // in ticks
            order.setState(OrderState.NEW);

            DirectBuffer buffer = order.getBuffer();

            // Try sending the order
            while (true) {
                long result = publication.offer(buffer, 0, Order.SIZE);
                if (result > 0) {
                    System.out.println("Order sent successfully! ID=" + order.getOrderId());
                    break;
                } else {
                    // backoff strategy
                    Thread.sleep(1);
                }
            }
        }
    }
}
