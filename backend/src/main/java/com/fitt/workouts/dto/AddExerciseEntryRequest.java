package com.fitt.workouts.dto;

import jakarta.validation.constraints.NotNull;

public record AddExerciseEntryRequest(

        @NotNull(message = "Exercise ID is required")
        Long exerciseId
) {}
