package com.fitt.measurements.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Kilograms. A client that works in pounds converts before it sends, which is why
// the column keeps two decimal places: one place of a kilogram is too coarse to
// give back the pounds that were typed.
// The upper bound keeps the value inside the decimal(6,2) column.
public record LogBodyWeightRequest(
        @NotNull LocalDate loggedAt,
        @NotNull @DecimalMin("0.1") @DecimalMax(value = "700", message = "Weight must be 700 or less")
        @Digits(integer = 9, fraction = 2, message = "Weight can have at most 2 decimal places")
        BigDecimal weight
) {
}
