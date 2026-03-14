package com.satya.oms.disruptor;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.satya.oms.sbe.OrderDecoder;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.MessageHeaderDecoder;
import com.satya.oms.sbe.MessageHeaderEncoder;
import com.satya.oms.sbe.OrderState;
import com.satya.oms.sbe.Side;

public class OrderEvent {
    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]); // Increased size for SBE message
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderDecoder orderDecoder = new OrderDecoder();
    private final OrderEncoder orderEncoder = new OrderEncoder();

    public void setBuffer(DirectBuffer srcBuffer, int offset) {
        // Copy the entire SBE message (including header)
        int length = MessageHeaderDecoder.ENCODED_LENGTH + OrderDecoder.BLOCK_LENGTH;
        buffer.putBytes(0, srcBuffer, offset, length);
    }

    // Getter methods - decode on demand
    public long getOrderId() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.orderId();
    }

    public long getSymbolId() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.symbolId();
    }

    public short getSide() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.side().value();
    }

    public long getQuantity() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.quantity();
    }

    public long getPrice() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.price();
    }

    public short getState() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.state().value();
    }

    public long getFilledQty() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.filledQty();
    }

    public long getRemainingQty() {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return orderDecoder.remainingQty();
    }

    // Setter methods - encode changes
    public void setBufferQty(long qty) {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        long orderId = orderDecoder.orderId();
        long symbolId = orderDecoder.symbolId();
        Side side = orderDecoder.side();
        long price = orderDecoder.price();
        OrderState state = orderDecoder.state();
        long filledQty = orderDecoder.filledQty();
        
        // Re-encode with new quantity
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .orderId(orderId)
            .symbolId(symbolId)
            .side(side)
            .quantity(qty)
            .price(price)
            .state(state)
            .filledQty(filledQty)
            .remainingQty(qty - filledQty);
    }

    public void setBufferState(OrderState newState) {
        orderDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        long orderId = orderDecoder.orderId();
        long symbolId = orderDecoder.symbolId();
        Side side = orderDecoder.side();
        long quantity = orderDecoder.quantity();
        long price = orderDecoder.price();
        long filledQty = orderDecoder.filledQty();
        long remainingQty = orderDecoder.remainingQty();
        
        // Re-encode with new state
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .orderId(orderId)
            .symbolId(symbolId)
            .side(side)
            .quantity(quantity)
            .price(price)
            .state(newState)
            .filledQty(filledQty)
            .remainingQty(remainingQty);
    }
}
