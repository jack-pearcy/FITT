package com.fitt.workouts.dto;

import com.fitt.workouts.domain.MuscleGroup;

public record ExerciseResponse(
        Long id,
        String name,
        MuscleGroup muscleGroup,
        boolean custom
) {}
