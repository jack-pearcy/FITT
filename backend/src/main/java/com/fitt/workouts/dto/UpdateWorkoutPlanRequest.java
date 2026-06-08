package com.fitt.workouts.dto;

import com.fitt.workouts.domain.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateWorkoutPlanRequest(

        @NotBlank(message = "Plan name is required")
        @Size(max = 100, message = "Plan name must be 100 characters or fewer")
        String name,

        @NotNull(message = "Days per week is required")
        @Min(value = 1, message = "Minimum 1 day per week")
        @Max(value = 7, message = "Maximum 7 days per week")
        Integer daysPerWeek,

        @NotNull(message = "Split type is required")
        SplitType splitType,

        @NotEmpty(message = "At least one training day is required")
        @Valid
        List<CreateWorkoutPlanDayRequest> days
) {}
