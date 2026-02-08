package com.satya.oms.core;

import com.satya.oms.disruptor.OrderEvent;

import java.util.ArrayList;
import com.google.common.annotations.VisibleForTesting;
import java.util.Comparator;
import java.util.List;

public class OMSOrderBook {

    private final List<OrderEvent> buyOrders = new ArrayList<>();
    private final List<OrderEvent> sellOrders = new ArrayList<>();

    // Add order to the book
    public void addOrder(OrderEvent order) {
        if (order.getSide() == 0) {
            buyOrders.add(order);
            buyOrders.sort(Comparator.comparingLong(OrderEvent::getPrice).reversed());
        } else {
            sellOrders.add(order);
            sellOrders.sort(Comparator.comparingLong(OrderEvent::getPrice));
        }
    }

    // Simple matching: match top buy vs top sell
    public void matchOrders() {
        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            OrderEvent buy = buyOrders.get(0);
            OrderEvent sell = sellOrders.get(0);

            System.out.printf("Open buy: %d Open sell: %d\n", buyOrders.size(), sellOrders.size());

            if (buy.getPrice() >= sell.getPrice()) {
                long tradeQty = Math.min(buy.getQuantity(), sell.getQuantity());
                System.out.printf("Matched: BuyID=%d SellID=%d Qty=%d Price=%d%n",
                        buy.getOrderId(), sell.getOrderId(), tradeQty, sell.getPrice());

                // Update quantities
                if (buy.getQuantity() == tradeQty) buyOrders.remove(0);
                else buyOrders.get(0).setBufferQty(buy.getQuantity() - tradeQty);

                if (sell.getQuantity() == tradeQty) sellOrders.remove(0);
                else sellOrders.get(0).setBufferQty(sell.getQuantity() - tradeQty);
            } else {
                break; // no more matches
            }
        }
    }

    @VisibleForTesting
    public  List<OrderEvent> getBuyOrders() {
        return buyOrders;
    }

    @VisibleForTesting
    public  List<OrderEvent> getSellOrders() {
        return sellOrders;
    }
}
