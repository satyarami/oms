package com.satya.oms.aeron;

import com.satya.oms.disruptor.OMSCore;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

public class OrderSubscriber implements Runnable {
    // IPC channel to the standalone media driver
    private static final String CHANNEL = "aeron:ipc?term-length=64k";
    private static final int STREAM_ID = 1001;

    private final Aeron aeron;
    private final Subscription subscription;
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1);

    private final OMSCore omsCore;

    public OrderSubscriber(Aeron aeron, OMSCore omsCore) {
        this.aeron = aeron;
        this.subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
        this.omsCore = omsCore;
    }

    @Override
    public void run() {
        FragmentHandler fragmentHandler = this::onFragment;

        while (!Thread.currentThread().isInterrupted()) {
            int fragments = subscription.poll(fragmentHandler, 10);
            if (fragments == 0) {
                idleStrategy.idle();
            }
        }
    }

    private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        long orderId = buffer.getLong(offset);
        int symbolId = buffer.getInt(offset + 8);
        byte side = buffer.getByte(offset + 12);
        long quantity = buffer.getLong(offset + 13);
        long price = buffer.getLong(offset + 21);
        byte state = buffer.getByte(offset + 29);

        System.out.printf("Received Order: id=%d symbol=%d side=%d qty=%d price=%d state=%d%n",
                orderId, symbolId, side, quantity, price, state);

        omsCore.publish(buffer, offset); // push directly into Disruptor
    }

    public static void main(String[] args) {
        // Connect to standalone Media Driver
        Aeron.Context ctx = new Aeron.Context();

        try (Aeron aeron = Aeron.connect(ctx)) {
            System.out.println("Connected to standalone Aeron Media Driver.");

            // Create OMS core with Disruptor
            OMSCore omsCore = new OMSCore();

            // Start subscriber
            OrderSubscriber subscriber = new OrderSubscriber(aeron, omsCore);
            Thread subscriberThread = new Thread(subscriber);
            subscriberThread.start();

            System.out.println("OrderSubscriber started. Receiving orders...");

            // Keep running for demo (10 seconds)
            Thread.sleep(100_000_000);

            // Stop
            subscriberThread.interrupt();
            subscriberThread.join();

            System.out.println("Subscriber stopped. Exiting Main.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}


