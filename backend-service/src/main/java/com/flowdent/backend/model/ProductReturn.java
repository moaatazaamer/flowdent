package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Returned goods, in either direction. Amounts are EGP.
 * <ul>
 *   <li>CUSTOMER_RETURN (from a client): stock goes back up only if {@code backToStock}.</li>
 *   <li>SUPPLIER_RETURN (to a provider): stock goes down.</li>
 * </ul>
 * {@code settlement} says whether {@code amount} was taken off the open balance (CREDIT)
 * or paid back as money (CASH).
 */
@Entity
@Table(name = "product_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    @Column(nullable = false)
    private LocalDate returnDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "category", "description", "descriptionAr"})
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /** Value of the returned goods (refund to the client / credit from the provider). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private ReturnSettlement settlement = ReturnSettlement.CREDIT;

    /** Customer returns: the client and (optionally) the original order. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Client client;

    private Long orderId;

    /** Supplier returns: the provider the goods went back to. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    /** Customer returns: whether the item is resellable and was put back in stock. */
    @Builder.Default
    private Boolean backToStock = true;

    /** Product cost per unit at the time of the return (for profit calculations). */
    @Column(precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(length = 1000)
    private String reason;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (settlement == null) settlement = ReturnSettlement.CREDIT;
    }
}
