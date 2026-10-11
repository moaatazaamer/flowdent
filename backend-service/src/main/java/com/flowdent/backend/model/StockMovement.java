package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line in the stock ledger: every change to a product's quantity is recorded here. */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_mov_product", columnList = "product_id"),
        @Index(name = "idx_stock_mov_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "category", "description", "descriptionAr"})
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockMovementType type;

    /** Signed change: positive adds stock, negative removes it. */
    @Column(nullable = false)
    private Integer quantityChange;

    @Column(nullable = false)
    private Integer stockAfter;

    /** Cost per unit in the store currency (receipts and write-offs). */
    @Column(precision = 12, scale = 4)
    private BigDecimal unitCost;

    /** Human-readable source, e.g. "Order #12" or "PO #3". */
    private String reference;

    @Column(length = 1000)
    private String note;

    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
