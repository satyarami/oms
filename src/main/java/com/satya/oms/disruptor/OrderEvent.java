package com.satya.oms.disruptor;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class OrderEvent {
    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[30]); // same size as Order

    public void setBuffer(DirectBuffer srcBuffer, int offset) {
        buffer.putBytes(0, srcBuffer, offset, 30);
    }

    public long getOrderId() { return buffer.getLong(0); }
    public int getSymbolId() { return buffer.getInt(8); }
    public byte getSide() { return buffer.getByte(12); }
    public long getQuantity() { return buffer.getLong(13); }
    public long getPrice() { return buffer.getLong(21); }
    public byte getState() { return buffer.getByte(29); }

    public void setBufferQty(long qty) {
        buffer.putLong(13, qty); // same offset as quantity
    }

    public void setBufferState(byte state) {
        buffer.putByte(29, state);
    }
}
