package com.fitt.workouts.dto;

import java.math.BigDecimal;

public record SetEntryResponse(
        Long id,
        int setNumber,
        Integer reps,
        BigDecimal weight,
        BigDecimal rpe,
        boolean completed
) {}
