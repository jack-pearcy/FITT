package com.fitt.workouts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateWorkoutSessionRequest(

        @NotNull(message = "Start time is required")
        Instant startedAt,

        // Null when session is created outside of a plan context
        Long planId,

        @Size(max = 5000, message = "Notes must be 5000 characters or fewer")
        String notes
) {}