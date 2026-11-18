package com.fitt.workouts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// Upper bounds mirror the column sizes (weight decimal(6,2), rpe decimal(3,1)) so an
// oversized value is a clean 400 here instead of a database error further down.
// The digit limits mirror the scale: without them the database rounds a longer
// number silently, and the value saved is not the value sent.
// The whole-number part is left wide on purpose. The maximum already limits the
// size, and a tight limit here would report one oversized number twice.
public record LogSetRequest(

        @NotNull(message = "Reps is required")
        @Min(value = 0, message = "Reps cannot be negative")
        @Max(value = 1000, message = "Reps must be 1000 or fewer")
        Integer reps,

        // null for bodyweight exercises
        @DecimalMin(value = "0", message = "Weight cannot be negative")
        @DecimalMax(value = "9999.99", message = "Weight must be 9999.99 or less")
        @Digits(integer = 9, fraction = 2, message = "Weight can have at most 2 decimal places")
        BigDecimal weight,

        // optional RPE, the standard 0 to 10 scale
        @DecimalMin(value = "0", message = "RPE cannot be negative")
        @DecimalMax(value = "10", message = "RPE must be 10 or less")
        @Digits(integer = 9, fraction = 1, message = "RPE can have at most 1 decimal place")
        BigDecimal rpe
) {}
