package com.satya.oms.disruptor;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;

import org.agrona.concurrent.UnsafeBuffer;
import com.lmax.disruptor.EventHandler;
import com.satya.oms.config.OMSConfig;
import com.satya.oms.core.OMSOrderBook;
import com.satya.oms.gateway.MarketGateway;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.OrderState;
import com.satya.oms.sbe.Side;
import io.aeron.Aeron;
import io.aeron.Publication;

public class OrderEventHandler implements EventHandler<OrderEvent> {

    private static final String CHANNEL = OMSConfig.getAeronChannel();
    private static final int STREAM_ID = OMSConfig.getAeronOutStreamId();

    private final OMSOrderBook orderBook = new OMSOrderBook();
    private final MarketGateway gateway = new MarketGateway();
    final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(512);
    final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final OrderEncoder orderEncoder = new OrderEncoder();
    private final Publication publication;
    private final Random random = new Random();
    private long executionIdCounter = System.currentTimeMillis() * 1000; // Unique execution ID base

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
                publish(event, 0);
                return;
            }
            if (event.getQuantity() > OMSConfig.getMaxOrderQuantity()) {
                System.out.println("Rejected order exceeding max qty: " + event.getOrderId());
                event.setBufferState((byte) OrderState.REJECTED.value());
                publish(event, 0);
                return;
            }
            orderBook.addOrder(event);
            orderBook.matchOrders();
            gateway.sendOrder(event);
          
            publish(event, 1);
        } catch (Exception e) {
            System.err.println("Exception in onEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void publish(OrderEvent event, int fillCount) {
    	System.out.println("Publishing order: ID=" + event.getOrderId() + " state=" + event.getStateEnum() + " filledQty=" + event.getFilledQty() + " remainingQty=" + event.getRemainingQty());
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .orderId(event.getOrderId())
                .symbolId(event.getSymbolId())
                .side(Side.get(event.getSide()))
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .state(event.getStateEnum())
                .filledQty(event.getFilledQty())
                .remainingQty(event.getRemainingQty());
        
        // Generate random fills if fillCount > 0
        if (fillCount > 0 && event.getFilledQty() > 0) {
            long totalFilledQty = event.getFilledQty();
            int numFills = 1 + random.nextInt(3); // 1-3 fills
            long[] fillQuantities = generateRandomFills(totalFilledQty, numFills);
            
            OrderEncoder.FillsEncoder fillsEncoder = orderEncoder.fillsCount(fillQuantities.length);
            for (int i = 0; i < fillQuantities.length; i++) {
                fillsEncoder.next()
                    .fillQty(fillQuantities[i])
                    .fillPrice(event.getPrice())
                    .executionId(executionIdCounter++);
                System.out.println("  Fill " + (i + 1) + ": qty=" + fillQuantities[i] + ", price=" + event.getPrice() + ", execId=" + (executionIdCounter - 1));
            }
        } else {
            orderEncoder.fillsCount(0);
        }
        
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

    /**
     * Generates random fill quantities that sum to totalQty.
     * @param totalQty the total quantity to distribute across fills
     * @param numFills the number of fills to generate (1-3)
     * @return array of fill quantities summing to totalQty
     */
    private long[] generateRandomFills(long totalQty, int numFills) {
        if (numFills <= 0 || totalQty <= 0) {
            return new long[0];
        }
        // Ensure we don't have more fills than quantity
        numFills = (int) Math.min(numFills, totalQty);
        long[] fills = new long[numFills];
        
        if (numFills == 1) {
            fills[0] = totalQty;
        } else {
            long remaining = totalQty;
            for (int i = 0; i < numFills - 1; i++) {
                // Ensure each fill gets at least 1, and leave enough for remaining fills
                long maxForThisFill = remaining - (numFills - i - 1);
                long minForThisFill = 1;
                if (maxForThisFill <= minForThisFill) {
                    fills[i] = minForThisFill;
                } else {
                    fills[i] = minForThisFill + (long)(random.nextDouble() * (maxForThisFill - minForThisFill));
                }
                remaining -= fills[i];
            }
            fills[numFills - 1] = remaining; // Last fill gets the remainder
        }
        return fills;
    }
}
