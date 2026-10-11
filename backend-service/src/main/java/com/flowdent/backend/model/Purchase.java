package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Goods bought from a provider. All amounts are in EGP.
 * Extra costs (shipping, customs, other) are spread over the items by value when the
 * purchase is received, so each product's cost price includes them.
 */
@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.ORDERED;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    private LocalDate receivedDate;

    /** Provider's invoice number, if any. */
    private String invoiceNumber;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal customsCost = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal otherCost = BigDecimal.ZERO;

    @Column(length = 2000)
    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void addItem(PurchaseItem item) {
        item.setPurchase(this);
        items.add(item);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Value of the goods, owed to the provider. */
    @JsonProperty
    public BigDecimal getGoodsTotal() {
        return items.stream()
                .map(i -> nz(i.getUnitCost()).multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @JsonProperty
    public BigDecimal getExtraCosts() {
        return nz(shippingCost).add(nz(customsCost)).add(nz(otherCost)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Goods plus extra costs: the full cost of bringing the stock in. */
    @JsonProperty
    public BigDecimal getTotalCost() {
        return getGoodsTotal().add(getExtraCosts());
    }
}
