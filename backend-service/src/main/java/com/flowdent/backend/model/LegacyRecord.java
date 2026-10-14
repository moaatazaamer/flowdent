package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One imported old record. These are history only: they appear in finance reports and carry
 * opening balances, but never change stock.
 */
@Entity
@Table(name = "legacy_records", indexes = {
        @Index(name = "idx_legacy_date", columnList = "recordDate"),
        @Index(name = "idx_legacy_type", columnList = "type")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegacyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private LegacyBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LegacyType type;

    @Column(nullable = false)
    private LocalDate recordDate;

    /** Client or provider name as written in the file. */
    private String partyName;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Expenses only. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExpenseCategory category;

    /** Old invoice number. */
    private String reference;

    @Column(length = 1000)
    private String description;

    /** Total amount in EGP (for balances: what is owed; negative = credit). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Sales and purchases: how much was paid. Empty = paid in full. */
    @Column(precision = 14, scale = 2)
    private BigDecimal paidAmount;

    /** Sales only: cost of the goods sold, if known. */
    @Column(precision = 14, scale = 2)
    private BigDecimal costAmount;

    /** What was really paid: the amount when the file did not say. */
    public BigDecimal paidOrFull() {
        return paidAmount != null ? paidAmount : amount;
    }
}
