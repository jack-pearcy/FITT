package com.fitt.identity.dto;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Saved biometrics as the user last entered them. Height and weight stay metric.
public record BiometricsResponse(
        BigDecimal heightCm,
        BigDecimal weightKg,
        LocalDate dateOfBirth,
        BiologicalSex biologicalSex,
        ActivityLevel activityLevel,
        UnitsPreference unitsPreference,
        Instant updatedAt
) {}
