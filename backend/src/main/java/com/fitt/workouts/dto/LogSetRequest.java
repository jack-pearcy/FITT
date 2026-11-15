package com.fitt.workouts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// Upper bounds mirror the column sizes (weight decimal(6,2), rpe decimal(3,1)) so an
// oversized value is a clean 400 here instead of a database error further down.
public record LogSetRequest(

        @NotNull(message = "Reps is required")
        @Min(value = 0, message = "Reps cannot be negative")
        @Max(value = 1000, message = "Reps must be 1000 or fewer")
        Integer reps,

        // null for bodyweight exercises
        @DecimalMin(value = "0", message = "Weight cannot be negative")
        @DecimalMax(value = "9999.99", message = "Weight must be 9999.99 or less")
        BigDecimal weight,

        // optional RPE, the standard 0 to 10 scale
        @DecimalMin(value = "0", message = "RPE cannot be negative")
        @DecimalMax(value = "10", message = "RPE must be 10 or less")
        BigDecimal rpe
) {}
