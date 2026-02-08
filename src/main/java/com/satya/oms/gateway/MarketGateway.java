package com.satya.oms.gateway;

import com.satya.oms.disruptor.OrderEvent;
import com.satya.oms.model.OrderState;

import java.util.concurrent.ThreadLocalRandom;

public class MarketGateway {

    /**
     * Simulate sending an order to the market.
     * Immediately returns ACK or partial FILL randomly.
     */
    public void sendOrder(OrderEvent order) {
        System.out.printf("Gateway received order: ID=%d side=%d qty=%d price=%d%n",
                order.getOrderId(), order.getSide(), order.getQuantity(), order.getPrice());

        // Simulate random market behavior
        int action = ThreadLocalRandom.current().nextInt(100);
        if (action < 70) {
            // Fully filled
            order.setBufferState(OrderState.FILLED);
            System.out.printf("Order FILLED: ID=%d qty=%d%n", order.getOrderId(), order.getQuantity());
        } else if (action < 90) {
            // Partially filled
            long filledQty = order.getQuantity() / 2;
            order.setBufferQty(order.getQuantity() - filledQty);
            order.setBufferState(OrderState.PARTIALLY_FILLED);
            System.out.printf("Order PARTIALLY FILLED: ID=%d filledQty=%d remainingQty=%d%n",
                    order.getOrderId(), filledQty, order.getQuantity() - filledQty);
        } else {
            // Rejected
            order.setBufferState(OrderState.REJECTED);
            System.out.printf("Order REJECTED: ID=%d%n", order.getOrderId());
        }
    }
}
