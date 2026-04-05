package com.satya.oms.gateway;

import org.agrona.DirectBuffer;

/**
 * Callback interface for receiving SBE-encoded gateway responses.
 * All buffers contain SBE messages (with message header) starting at the given offset.
 */
public interface GatewayListener {

    /** Called when the gateway acknowledges an order (AckEvent). */
    void onAck(DirectBuffer buffer, int offset, int length);

    /** Called when the gateway rejects an order (RejectEvent). */
    void onReject(DirectBuffer buffer, int offset, int length);

    /** Called for each fill the gateway produces (FillEvent). */
    void onFill(DirectBuffer buffer, int offset, int length);

    /** Called when no more fills will arrive for the order (DoneEvent). */
    void onDone(DirectBuffer buffer, int offset, int length);
}
