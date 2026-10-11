package com.flowdent.backend.model;

public enum ReturnSettlement {
    /** The amount is deducted from the open balance (credit note). */
    CREDIT,
    /** The amount was paid back in cash / transfer. Balance unchanged. */
    CASH
}
