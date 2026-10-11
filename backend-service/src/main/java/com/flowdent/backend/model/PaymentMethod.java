package com.flowdent.backend.model;

public enum PaymentMethod {
    ONLINE_PAYMENT,
    CASH_ON_DELIVERY,
    POS_ON_DELIVERY,
    /** Bank transfer / InstaPay (staff-entered sales). */
    BANK_TRANSFER,
    /** On account: the client pays later (staff-entered sales). */
    CREDIT
}
