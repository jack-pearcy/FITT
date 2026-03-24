package com.fitt.measurements.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BodyWeightEntryResponse(
        Long id,
        LocalDate loggedAt,
        BigDecimal weight,
        Instant createdAt
) {
}
