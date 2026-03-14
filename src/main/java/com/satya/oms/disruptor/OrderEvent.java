package com.satya.oms.disruptor;

import com.satya.oms.sbe.OrderDecoder;
import com.satya.oms.sbe.OrderEncoder;
import com.satya.oms.sbe.OrderState;
import com.satya.oms.sbe.Side;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class OrderEvent {

    public static final int BLOCK_LENGTH = OrderEncoder.BLOCK_LENGTH;

    /** Internal buffer that holds exactly one SBE Order message (no header). */
    private final UnsafeBuffer buffer =
            new UnsafeBuffer(ByteBuffer.allocateDirect(BLOCK_LENGTH));

    /** Encoder wraps the internal buffer at offset 0. */
    private final OrderEncoder encoder = new OrderEncoder();

    /** Decoder wraps the same internal buffer at offset 0. */
    private final OrderDecoder decoder = new OrderDecoder();

    public OrderEvent() {
        encoder.wrap(buffer, 0);
        decoder.wrap(buffer, 0, BLOCK_LENGTH, OrderDecoder.SCHEMA_VERSION);
    }

    // -----------------------------------------------------------------------
    // Population – copy raw SBE bytes from an incoming Aeron/network buffer.
    // The source buffer is expected to already have the Order fields starting
    // at the given offset (i.e. past any message header).
    // -----------------------------------------------------------------------
    public void setBuffer(DirectBuffer srcBuffer, int offset) {
        buffer.putBytes(0, srcBuffer, offset, BLOCK_LENGTH);
        // re-wrap so encoder/decoder see the fresh data
        encoder.wrap(buffer, 0);
        decoder.wrap(buffer, 0, BLOCK_LENGTH, OrderDecoder.SCHEMA_VERSION);
    }

    // -----------------------------------------------------------------------
    // Readers – delegate to the SBE OrderDecoder
    // -----------------------------------------------------------------------
    public long getOrderId()      {
    	return decoder.orderId();
    	}
    public long getSymbolId()     { return decoder.symbolId(); }
    public Side getSideEnum()     { return decoder.side(); }
    /** Raw side byte: 0 = BUY, 1 = SELL – kept for OMSOrderBook compatibility. */
    public byte getSide()         { return (byte) decoder.side().value(); }
    public long getQuantity()     { return decoder.quantity(); }
    public long getPrice()        { return decoder.price(); }
    public OrderState getStateEnum() { return decoder.state(); }
    /** Raw state byte – kept for callers that compare against OrderState.value(). */
    public byte getState()        { return (byte) decoder.state().value(); }
    public long getFilledQty()    { return decoder.filledQty(); }
    public long getRemainingQty() { return decoder.remainingQty(); }

    // -----------------------------------------------------------------------
    // Writers – delegate to the SBE OrderEncoder
    // -----------------------------------------------------------------------
    /** Updates the quantity field (used by the order book during matching). */
    public void setBufferQty(long qty) {
        encoder.quantity(qty);
    }

    /** Updates the order state field. */
    public void setBufferState(byte state) {
        encoder.state(OrderState.get((short)(state & 0xFF)));
    }
}