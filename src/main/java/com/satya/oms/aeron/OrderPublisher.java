package com.satya.oms.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.OrderState;
import com.satya.oms.sbe.Side;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class OrderPublisher {

    private static final String CHANNEL = "aeron:ipc?term-length=64k";
    private static final int STREAM_ID = 1001;

    public static void main(String[] args) throws InterruptedException {
        Aeron.Context ctx = new Aeron.Context();

        try (Aeron aeron = Aeron.connect(ctx);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

            System.out.println("Publisher connected to Aeron Media Driver.");

            // Create buffer for encoding
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(256);
            final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
            final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
            final OrderEncoder orderEncoder = new OrderEncoder();

            // Create an order using SBE encoder
            long orderId = ThreadLocalRandom.current().nextLong(1, 1_000_000);
            orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .orderId(orderId)
                .symbolId(1001)
                .side(Side.BUY)
                .quantity(500)
                .price(12345)
                .state(OrderState.NEW)
                .filledQty(0)
                .remainingQty(500);

            int length = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();

            // Try sending the order
            while (true) {
                long result = publication.offer(buffer, 0, length);
                if (result > 0) {
                    System.out.println("Order sent successfully! ID=" + orderId);
                    break;
                } else {
                    // backoff strategy
                    Thread.sleep(100);
                }
            }
        }
    }
}
