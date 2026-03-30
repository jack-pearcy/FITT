package com.fitt.identity.dto;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveBiometricsRequest(
        @NotNull BigDecimal heightCm,
        @NotNull BigDecimal weightKg,
        @NotNull LocalDate dateOfBirth,
        @NotNull BiologicalSex biologicalSex,
        @NotNull ActivityLevel activityLevel,
        @NotNull UnitsPreference unitsPreference
) {}