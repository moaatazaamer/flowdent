package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "category", "description", "descriptionAr"})
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /** Provider's price per unit, EGP. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** Unit cost including this item's share of the extra costs; set on receipt. */
    @Column(precision = 12, scale = 4)
    private BigDecimal landedUnitCost;
}
