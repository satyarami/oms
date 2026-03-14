
package com.satya.oms.core;

import com.satya.oms.disruptor.OrderEvent;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.Side;
import com.satya.oms.sbe.OrderState;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class OMSOrderBookTest {

    private OMSOrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OMSOrderBook();
    }

    @Test
    void testAddBuyOrder() {
        OrderEvent buyOrder = createOrder(1001, Side.BUY, 100, 50000);
        orderBook.addOrder(buyOrder);

        assertEquals(1, orderBook.getBuyOrders().size());
        assertEquals(0, orderBook.getSellOrders().size());
    }

    @Test
    void testAddSellOrder() {
        OrderEvent sellOrder = createOrder(1001, Side.SELL, 100, 50000);
        orderBook.addOrder(sellOrder);

        assertEquals(0, orderBook.getBuyOrders().size());
        assertEquals(1, orderBook.getSellOrders().size());
    }

    private OrderEvent createOrder(int symbolId, Side side, int quantity, int price) {
        // Create buffer and SBE encoder
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(512);
        final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final OrderEncoder orderEncoder = new OrderEncoder();

        // Encode order
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .orderId(ThreadLocalRandom.current().nextLong(1, 1_000_000))
            .symbolId(symbolId)
            .side(side)
            .quantity(quantity)
            .price(price)
            .state(OrderState.NEW)
            .filledQty(0)
            .remainingQty(quantity);

        // Create OrderEvent and set buffer
        OrderEvent event = new OrderEvent();
        event.setBuffer(buffer, 0);

        return event;
    }
}
