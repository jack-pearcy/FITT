package com.fitt.workouts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddExerciseEntryRequest(

        @NotNull(message = "Exercise ID is required")
        Long exerciseId,

        // Optional. The same id sent again adds nothing.
        @Pattern(regexp = ClientIds.PATTERN, message = ClientIds.MESSAGE)
        String clientId
) {
    public AddExerciseEntryRequest(Long exerciseId) {
        this(exerciseId, null);
    }
}
