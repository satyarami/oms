package com.satya.oms.disruptor;

import com.lmax.disruptor.EventHandler;
import com.satya.oms.core.OMSOrderBook;
import com.satya.oms.gateway.MarketGateway;
import com.satya.oms.model.OrderState;

public class OrderEventHandler implements EventHandler<OrderEvent> {

    private final OMSOrderBook orderBook = new OMSOrderBook();
    private final MarketGateway gateway = new MarketGateway();

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        // Validation
        if (event.getQuantity() <= 0 || event.getPrice() <= 0) {
            System.out.println("Rejected invalid order: " + event.getOrderId());
            event.setBufferState(OrderState.REJECTED);
            return;
        }

        // Risk check
        if (event.getQuantity() > 1000) {
            System.out.println("Rejected order exceeding max qty: " + event.getOrderId());
            event.setBufferState(OrderState.REJECTED);
            return;
        }

        // Add to order book
        orderBook.addOrder(event);

        // Matching
        orderBook.matchOrders();

        // Send to simulated market gateway
        gateway.sendOrder(event);
    }
}
