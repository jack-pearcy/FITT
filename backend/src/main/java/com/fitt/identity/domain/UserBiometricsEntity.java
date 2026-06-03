package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_biometrics")
public class UserBiometricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bounded context rule. Stores the ID only, never imports UserEntity.
    @Column(nullable = false, unique = true)
    private Long ownerUserId;

    // Always stored in metric internally. Frontend converts for display.
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BiologicalSex biologicalSex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitsPreference unitsPreference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserBiometricsEntity() {}

    public UserBiometricsEntity(
            Long ownerUserId,
            BigDecimal heightCm,
            BigDecimal weightKg,
            LocalDate dateOfBirth,
            BiologicalSex biologicalSex,
            ActivityLevel activityLevel,
            UnitsPreference unitsPreference
    ) {
        this.ownerUserId = ownerUserId;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.dateOfBirth = dateOfBirth;
        this.biologicalSex = biologicalSex;
        this.activityLevel = activityLevel;
        this.unitsPreference = unitsPreference;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Called when a user resubmits onboarding. No setter spray.
    public void update(
            BigDecimal heightCm,
            BigDecimal weightKg,
            LocalDate dateOfBirth,
            BiologicalSex biologicalSex,
            ActivityLevel activityLevel,
            UnitsPreference unitsPreference
    ) {
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.dateOfBirth = dateOfBirth;
        this.biologicalSex = biologicalSex;
        this.activityLevel = activityLevel;
        this.unitsPreference = unitsPreference;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getWeightKg() { return weightKg; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public BiologicalSex getBiologicalSex() { return biologicalSex; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public UnitsPreference getUnitsPreference() { return unitsPreference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}