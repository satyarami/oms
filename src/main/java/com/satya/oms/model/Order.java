package com.satya.oms.model;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class Order {
    public static final int SIZE = 8 + 4 + 1 + 8 + 8 + 1; // total bytes

    private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(SIZE));

    // Offsets
    private static final int OFFSET_ORDER_ID = 0;
    private static final int OFFSET_SYMBOL_ID = 8;
    private static final int OFFSET_SIDE = 12;
    private static final int OFFSET_QTY = 13;
    private static final int OFFSET_PRICE = 21;
    private static final int OFFSET_STATE = 29;

    // Setters (write directly into buffer)
    public void setOrderId(long id) { buffer.putLong(OFFSET_ORDER_ID, id); }
    public void setSymbolId(int symbol) { buffer.putInt(OFFSET_SYMBOL_ID, symbol); }
    public void setSide(byte side) { buffer.putByte(OFFSET_SIDE, side); }
    public void setQuantity(long qty) { buffer.putLong(OFFSET_QTY, qty); }
    public void setPrice(long price) { buffer.putLong(OFFSET_PRICE, price); }
    public void setState(byte state) { buffer.putByte(OFFSET_STATE, state); }

    // Getters (read directly from buffer)
    public long getOrderId() { return buffer.getLong(OFFSET_ORDER_ID); }
    public int getSymbolId() { return buffer.getInt(OFFSET_SYMBOL_ID); }
    public byte getSide() { return buffer.getByte(OFFSET_SIDE); }
    public long getQuantity() { return buffer.getLong(OFFSET_QTY); }
    public long getPrice() { return buffer.getLong(OFFSET_PRICE); }
    public byte getState() { return buffer.getByte(OFFSET_STATE); }

    public MutableDirectBuffer getBuffer() { return buffer; }
}
