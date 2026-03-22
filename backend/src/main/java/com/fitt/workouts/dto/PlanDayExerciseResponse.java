package com.fitt.workouts.dto;

public record PlanDayExerciseResponse(
        Long id,
        int orderIndex,
        int sets,
        int reps,
        ExerciseResponse exercise
) {}