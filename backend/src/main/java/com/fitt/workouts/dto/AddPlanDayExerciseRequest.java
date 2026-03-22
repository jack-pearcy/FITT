package com.fitt.workouts.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddPlanDayExerciseRequest(

        @NotNull(message = "Exercise ID is required")
        Long exerciseId,

        @NotNull(message = "Sets is required")
        @Min(value = 1, message = "Minimum 1 set")
        Integer sets,

        @NotNull(message = "Reps is required")
        @Min(value = 1, message = "Minimum 1 rep")
        Integer reps
) {}