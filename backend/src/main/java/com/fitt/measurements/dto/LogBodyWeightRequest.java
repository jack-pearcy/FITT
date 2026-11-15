package com.fitt.measurements.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// The upper bound keeps the value inside the decimal(5,1) column.
public record LogBodyWeightRequest(
        @NotNull LocalDate loggedAt,
        @NotNull @DecimalMin("0.1") @DecimalMax(value = "700", message = "Weight must be 700 or less")
        BigDecimal weight
) {
}
