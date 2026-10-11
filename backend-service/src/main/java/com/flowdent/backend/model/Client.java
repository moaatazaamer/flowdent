package com.flowdent.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A B2B buyer: a clinic, doctor, lab or hospital.
 * Optionally linked to a web account; online orders are attached to the client automatically.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clinic / practice name, or the doctor's name. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ClientType type = ClientType.CLINIC;

    private String contactName;
    private String phone;
    private String email;

    @Column(length = 1000)
    private String address;

    private String city;

    @Column(length = 2000)
    private String notes;

    /** Linked web account, if the client registered on the store. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (type == null) type = ClientType.CLINIC;
    }

    public boolean isRegistered() {
        return user != null;
    }
}
