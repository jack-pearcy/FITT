package com.fitt.workouts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWorkoutPlanDayRequest(

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index cannot be negative")
        Integer orderIndex,

        @NotBlank(message = "Day label is required")
        @Size(max = 50, message = "Day label must be 50 characters or fewer")
        String dayLabel,

        @Valid
        List<AddPlanDayExerciseRequest> exercises
) {}
