package com.satya.oms.model;

public final class OrderState {
    public static final byte NEW = 0;                // Newly received order
    public static final byte VALIDATED = 1;          // Passed validation and risk checks
    public static final byte SENT = 2;               // Sent to market (optional)
    public static final byte ACKED = 3;              // Acknowledged by market
    public static final byte PARTIALLY_FILLED = 4;   // Partially filled by market
    public static final byte FILLED = 5;             // Fully filled
    public static final byte REJECTED = 6;           // Rejected (validation, risk, or market)
}