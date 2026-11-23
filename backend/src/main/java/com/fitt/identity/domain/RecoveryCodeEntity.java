package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// One recovery code. The code is shown to the user once and never stored, only
// its hash, and it works a single time.
@Entity
@Table(name = "mfa_recovery_codes")
public class RecoveryCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // Hex SHA-256 of the code, 64 characters.
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecoveryCodeEntity() {}

    public RecoveryCodeEntity(Long userId, String codeHash, Instant createdAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
