package com.fitt.workouts.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SetEntryResponse(
        Long id,
        int setNumber,
        Integer reps,
        BigDecimal weight,
        BigDecimal rpe,
        boolean completed,
        // Both null for a set logged by a client that sends neither.
        String clientId,
        Instant performedAt
) {
    public SetEntryResponse(Long id, int setNumber, Integer reps, BigDecimal weight, BigDecimal rpe,
                            boolean completed) {
        this(id, setNumber, reps, weight, rpe, completed, null, null);
    }
}
