package com.satya.oms.gateway;

import com.satya.oms.disruptor.OrderEvent;
import com.satya.oms.sbe.OrderState;

import java.util.concurrent.ThreadLocalRandom;

public class MarketGateway {

    /**
     * Simulate sending an order to the market.
     * Immediately returns ACK or partial FILL randomly.
     */
    public void sendOrder(OrderEvent order) {
    	
        System.out.printf("Market Gateway received order: ID=%d side=%d qty=%d price=%d%n",
                order.getOrderId(), order.getSide(), order.getQuantity(), order.getPrice());

        // Simulate random market behavior
        int action = ThreadLocalRandom.current().nextInt(100);
        if (action < 70) {
            // Fully filled
            long qty = order.getQuantity();
            order.setBufferFilledQty(qty);
            order.setBufferRemainingQty(0);
            order.setBufferState((byte)OrderState.FILLED.value());
            System.out.printf("Order FILLED: ID=%d qty=%d%n", order.getOrderId(), qty);
        } else if (action < 90) {
            // Partially filled
            long filledQty = order.getQuantity() / 2;
            long remainingQty = order.getQuantity() - filledQty;
            order.setBufferFilledQty(filledQty);
            order.setBufferRemainingQty(remainingQty);
            order.setBufferQty(remainingQty);
            order.setBufferState((byte)OrderState.PARTIALLY_FILLED.value());
            System.out.printf("Order PARTIALLY FILLED: ID=%d filledQty=%d remainingQty=%d%n",
                    order.getOrderId(), filledQty, remainingQty);
        } else {
            // Rejected
            order.setBufferFilledQty(0);
            order.setBufferRemainingQty(order.getQuantity());
            order.setBufferState((byte)OrderState.REJECTED.value());
            System.out.printf("Order REJECTED: ID=%d%n", order.getOrderId());
        }
    }
}