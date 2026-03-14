package com.satya.oms.disruptor;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class OrderEvent {
    public static final int BLOCK_LENGTH = 46; // matches SBE OrderEncoder.BLOCK_LENGTH

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[BLOCK_LENGTH]);

    public void setBuffer(DirectBuffer srcBuffer, int offset) {
        buffer.putBytes(0, srcBuffer, offset, BLOCK_LENGTH);
    }

    public long getOrderId()   { return buffer.getLong(0); }
    public int  getSymbolId()  { return buffer.getInt(8); }
    public byte getSide()      { return buffer.getByte(12); }
    public long getQuantity()  { return buffer.getLong(13); }
    public long getPrice()     { return buffer.getLong(21); }
    public byte getState()     { return buffer.getByte(29); }
    public long getFilledQty() { return buffer.getLong(30); }
    public long getRemainingQty() { return buffer.getLong(38); }

    public void setBufferQty(long qty) {
        buffer.putLong(13, qty);
    }

    public void setBufferState(byte state) {
        buffer.putByte(29, state);
    }
}