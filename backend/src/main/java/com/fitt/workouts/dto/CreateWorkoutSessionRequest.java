package com.fitt.workouts.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateWorkoutSessionRequest(

        @NotNull(message = "Start time is required")
        Instant startedAt,

        String notes
) {}