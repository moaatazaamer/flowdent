package com.flowdent.backend.model;

/** Kinds of old (pre-system) records that can be imported. */
public enum LegacyType {
    /** An old invoice to a client. */
    SALE,
    /** An old purchase from a provider. */
    PURCHASE,
    /** An old operating expense. */
    EXPENSE,
    /** What a client still owes the shop (carried into the client balance). */
    CLIENT_BALANCE,
    /** What the shop still owes a provider (carried into the provider balance). */
    PROVIDER_BALANCE
}
