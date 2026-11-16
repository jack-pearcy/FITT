package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// One issued refresh token. The token itself is never stored, only its hash,
// so a leaked table cannot be replayed against the API.
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long ownerUserId;

    // Hex SHA-256 of the token, 64 characters.
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    // Shared by every token descended from one login. A UUID, 36 characters.
    @Column(nullable = false, updatable = false, length = 36)
    private String familyId;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    // Null while the token is usable. Set when it is rotated, logged out, or
    // its family is revoked after a replay.
    @Column
    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(Long ownerUserId, String tokenHash, String familyId, Instant createdAt, Instant expiresAt) {
        this.ownerUserId = ownerUserId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
