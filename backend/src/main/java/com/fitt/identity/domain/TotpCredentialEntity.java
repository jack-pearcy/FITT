package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

// A user's authenticator app. The row exists from the moment setup starts and
// counts as on only once confirmedAt is set, which is when the user has typed a
// code the app produced.
@Entity
@Table(name = "user_totp")
public class TotpCredentialEntity {

    // The user id is the key: one authenticator per account.
    @Id
    @Column(name = "user_id")
    private Long userId;

    // Nonce plus AES-GCM ciphertext of the shared secret. Never the secret itself.
    @Column(name = "secret_encrypted", nullable = false, length = 128)
    private byte[] secretEncrypted;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    // The 30-second step of the last code that was accepted. A code is refused
    // unless its step is later, so a code seen over a shoulder cannot be reused.
    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TotpCredentialEntity() {}

    public TotpCredentialEntity(Long userId, byte[] secretEncrypted, Instant createdAt) {
        this.userId = userId;
        this.secretEncrypted = secretEncrypted;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public byte[] getSecretEncrypted() {
        return secretEncrypted;
    }

    public long getLastUsedStep() {
        return lastUsedStep;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    // Setup was started again before it was finished: a new secret replaces the old.
    public void restart(byte[] newSecretEncrypted, Instant now) {
        this.secretEncrypted = newSecretEncrypted;
        this.lastUsedStep = 0;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.createdAt = now;
    }

    public void confirm(Instant now) {
        this.confirmedAt = now;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public long secondsUntilUnlocked(Instant now) {
        return lockedUntil == null ? 0 : Math.max(1, Duration.between(now, lockedUntil).toSeconds());
    }

    public void acceptStep(long step) {
        this.lastUsedStep = step;
    }

    public void recordSuccess() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    // After maxFailures wrong codes in a row, no code is accepted for lockFor.
    // The count starts again afterwards, so the lock repeats, it does not grow.
    public void recordFailure(Instant now, int maxFailures, Duration lockFor) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxFailures) {
            this.lockedUntil = now.plus(lockFor);
            this.failedAttempts = 0;
        }
    }
}
