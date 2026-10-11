package com.flowdent.backend.model;

public enum PurchaseStatus {
    /** Ordered from the provider, goods not yet arrived. */
    ORDERED,
    /** Goods arrived and added to stock. */
    RECEIVED,
    CANCELLED
}
