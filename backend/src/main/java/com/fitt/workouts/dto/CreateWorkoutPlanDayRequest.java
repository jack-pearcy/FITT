package com.fitt.workouts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWorkoutPlanDayRequest(

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index cannot be negative")
        @Max(value = 100, message = "Order index must be 100 or less")
        Integer orderIndex,

        @NotBlank(message = "Day label is required")
        @Size(max = 50, message = "Day label must be 50 characters or fewer")
        String dayLabel,

        @Size(max = 50, message = "A day can have at most 50 exercises")
        @Valid
        List<AddPlanDayExerciseRequest> exercises
) {}
