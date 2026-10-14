package com.flowdent.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One import of old records from a file. Deleting the batch removes all of its records. */
@Entity
@Table(name = "legacy_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegacyBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    /** SHA-256 of the file, to stop the same file being imported twice. */
    @Column(nullable = false, length = 64)
    private String fileHash;

    @Column(updatable = false)
    private LocalDateTime importedAt;

    @Builder.Default private int sales = 0;
    @Builder.Default private int purchases = 0;
    @Builder.Default private int expenses = 0;
    @Builder.Default private int clientBalances = 0;
    @Builder.Default private int providerBalances = 0;

    @Column(precision = 14, scale = 2) @Builder.Default private BigDecimal totalSales = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) @Builder.Default private BigDecimal totalPurchases = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) @Builder.Default private BigDecimal totalExpenses = BigDecimal.ZERO;

    @PrePersist
    void onCreate() {
        if (importedAt == null) importedAt = LocalDateTime.now();
    }
}
