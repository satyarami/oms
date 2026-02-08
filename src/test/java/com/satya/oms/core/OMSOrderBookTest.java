
package com.satya.oms.core;

import com.satya.oms.disruptor.OrderEvent;
import com.satya.oms.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        OrderEvent buyOrder = createOrder(1001, 0, 100, 50000);
        orderBook.addOrder(buyOrder);

        assertEquals(1, orderBook.getBuyOrders().size());
        assertEquals(0, orderBook.getSellOrders().size());
    }

    @Test
    void testAddSellOrder() {
        OrderEvent sellOrder = createOrder(1001, 1, 100, 50000);
        orderBook.addOrder(sellOrder);

        assertEquals(0, orderBook.getBuyOrders().size());
        assertEquals(1, orderBook.getSellOrders().size());
    }

    private OrderEvent createOrder(int symbolId, int side, int quantity, int price) {
        // Create an order
        Order order = new Order();
        order.setOrderId(ThreadLocalRandom.current().nextLong(1, 1_000_000));
        order.setSymbolId(symbolId);
        order.setSide((byte) side); // 0 = Buy, 1 = Sell
        order.setQuantity(quantity);
        order.setPrice(price);   // in ticks

        OrderEvent event = new OrderEvent();
        event.setBuffer(order.getBuffer(), 0);

        return event;
    }
}
