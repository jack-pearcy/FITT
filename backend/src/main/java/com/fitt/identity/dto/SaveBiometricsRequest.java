package com.fitt.identity.dto;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.math.BigDecimal;
import java.time.LocalDate;

// Ranges are wide enough for any real person but stop values that would overflow the
// decimal(5,2) columns or push the TDEE formula into nonsense.
public record SaveBiometricsRequest(
        @NotNull @DecimalMin(value = "50", message = "Height must be at least 50 cm")
        @DecimalMax(value = "300", message = "Height must be 300 cm or less")
        @Digits(integer = 9, fraction = 2, message = "Height can have at most 2 decimal places")
        BigDecimal heightCm,

        @NotNull @DecimalMin(value = "20", message = "Weight must be at least 20 kg")
        @DecimalMax(value = "700", message = "Weight must be 700 kg or less")
        @Digits(integer = 9, fraction = 2, message = "Weight can have at most 2 decimal places")
        BigDecimal weightKg,

        @NotNull @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull BiologicalSex biologicalSex,
        @NotNull ActivityLevel activityLevel,
        @NotNull UnitsPreference unitsPreference
) {}
