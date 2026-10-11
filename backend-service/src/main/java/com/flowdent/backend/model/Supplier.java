package com.flowdent.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A provider (seller) the shop buys goods from. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String contactName;
    private String phone;
    private String email;

    @Column(length = 1000)
    private String address;

    @Column(length = 2000)
    private String notes;

    @Builder.Default
    private Boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
