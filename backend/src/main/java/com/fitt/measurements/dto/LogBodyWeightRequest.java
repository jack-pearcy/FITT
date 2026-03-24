package com.fitt.measurements.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LogBodyWeightRequest(
        @NotNull LocalDate loggedAt,
        @NotNull @DecimalMin("0.1") BigDecimal weight
) {
}
