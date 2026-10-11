package com.flowdent.backend.model;

public enum StockMovementType {
    /** Opening balance when a product is created or imported. */
    INITIAL,
    /** Customer order placed. */
    SALE,
    /** Customer order cancelled, stock put back. */
    SALE_CANCELLED,
    /** Goods received from a provider. */
    PURCHASE,
    /** Goods returned by a customer and put back on the shelf. */
    CUSTOMER_RETURN,
    /** Goods sent back to a provider. */
    SUPPLIER_RETURN,
    /** Manual correction, e.g. after a stock count. */
    ADJUSTMENT,
    /** Damaged, expired or lost goods written off. */
    DAMAGE
}
