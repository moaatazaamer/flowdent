package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String nameAr;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String descriptionAr;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"products", "hibernateLazyInitializer", "handler"})
    private Category category;

    /** Product brand shown in the shop, e.g. "Ora" (Flow Dent is Ora's exclusive agent). */
    @Column(length = 80)
    private String brand;

    /** Internal stock-keeping code. */
    @Column(unique = true, length = 64)
    private String sku;

    /**
     * Weighted-average landed cost per unit, in the store currency.
     * Internal: never serialised on public endpoints (see admin inventory API).
     */
    @JsonIgnore
    @Column(precision = 12, scale = 4)
    private BigDecimal costPrice;

    /** Minimum stock level before the product is flagged for reordering. Internal. */
    @JsonIgnore
    private Integer reorderLevel;

    public BigDecimal costOrZero() {
        return costPrice == null ? BigDecimal.ZERO : costPrice;
    }

    public int reorderLevelOrDefault() {
        return reorderLevel == null ? 10 : reorderLevel;
    }
}
