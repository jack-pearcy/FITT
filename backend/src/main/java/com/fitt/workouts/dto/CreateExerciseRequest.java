package com.fitt.workouts.dto;

import com.fitt.workouts.domain.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExerciseRequest(

        @NotBlank(message = "Exercise name is required")
        @Size(max = 100, message = "Exercise name must be 100 characters or fewer")
        String name,

        @NotNull(message = "Muscle group is required")
        MuscleGroup muscleGroup
) {}