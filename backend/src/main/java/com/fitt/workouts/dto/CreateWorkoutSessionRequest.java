package com.fitt.workouts.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateWorkoutSessionRequest(

        @NotNull(message = "Start time is required")
        Instant startedAt,

        // Null when session is created outside of a plan context
        Long planId,

        @Size(max = 5000, message = "Notes must be 5000 characters or fewer")
        String notes,

        // Optional. A start sent again with the same id returns the session the
        // first one made.
        @Pattern(regexp = ClientIds.PATTERN, message = ClientIds.MESSAGE)
        String clientId,

        // Optional. The day of the plan to train, from 0 in the plan's day order,
        // in place of the day the rotation points at. Needs planId.
        @Min(value = 0, message = "Plan day cannot be negative")
        Integer planDayIndex
) {
    public CreateWorkoutSessionRequest(Instant startedAt, Long planId, String notes) {
        this(startedAt, planId, notes, null, null);
    }

    public CreateWorkoutSessionRequest(Instant startedAt, Long planId, String notes, String clientId) {
        this(startedAt, planId, notes, clientId, null);
    }
}
