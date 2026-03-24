package com.fitt.measurements.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "body_weight_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_logged_at", columnNames = {"owner_user_id", "logged_at"})
})
public class BodyWeightEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    // LocalDate not Instant because body weight is a once per day log tied to a calendar date
    @Column(name = "logged_at", nullable = false)
    private LocalDate loggedAt;

    // BigDecimal not double because weight values need exact decimal representation
    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal weight;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BodyWeightEntryEntity() {
    }

    public BodyWeightEntryEntity(Long ownerUserId, LocalDate loggedAt, BigDecimal weight) {
        this.ownerUserId = ownerUserId;
        this.loggedAt = loggedAt;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public LocalDate getLoggedAt() {
        return loggedAt;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Used by upsert when a user re-logs weight for the same date
    public void updateWeight(BigDecimal weight) {
        this.weight = weight;
    }
}
