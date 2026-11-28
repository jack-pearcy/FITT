package com.fitt.workouts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// Replaces the numbers of a set already logged. The bounds are the ones
// LogSetRequest has, so an edit cannot store what logging would refuse.
// A null weight or rpe clears it, as a bodyweight set or a set with no RPE.
public record UpdateSetRequest(

        @NotNull(message = "Reps is required")
        @Min(value = 0, message = "Reps cannot be negative")
        @Max(value = 1000, message = "Reps must be 1000 or fewer")
        Integer reps,

        @DecimalMin(value = "0", message = "Weight cannot be negative")
        @DecimalMax(value = "9999.99", message = "Weight must be 9999.99 or less")
        @Digits(integer = 9, fraction = 2, message = "Weight can have at most 2 decimal places")
        BigDecimal weight,

        @DecimalMin(value = "0", message = "RPE cannot be negative")
        @DecimalMax(value = "10", message = "RPE must be 10 or less")
        @Digits(integer = 9, fraction = 1, message = "RPE can have at most 1 decimal place")
        BigDecimal rpe
) {}
