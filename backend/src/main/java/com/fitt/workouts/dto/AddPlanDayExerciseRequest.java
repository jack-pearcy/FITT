package com.fitt.workouts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddPlanDayExerciseRequest(

        @NotNull(message = "Exercise ID is required")
        Long exerciseId,

        @NotNull(message = "Sets is required")
        @Min(value = 1, message = "Minimum 1 set")
        @Max(value = 20, message = "Maximum 20 sets")
        Integer sets,

        @NotNull(message = "Reps is required")
        @Min(value = 1, message = "Minimum 1 rep")
        @Max(value = 200, message = "Maximum 200 reps")
        Integer reps
) {}