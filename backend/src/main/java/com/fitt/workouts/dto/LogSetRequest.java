package com.fitt.workouts.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LogSetRequest(

        @NotNull(message = "Reps is required")
        @Min(value = 0, message = "Reps cannot be negative")
        Integer reps,

        // null for bodyweight exercises
        BigDecimal weight,

        // optional RPE
        BigDecimal rpe
) {}