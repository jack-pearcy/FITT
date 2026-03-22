package com.fitt.workouts.dto;

import java.util.List;

public record WorkoutPlanDayResponse(
        Long id,
        int orderIndex,
        String dayLabel,
        List<PlanDayExerciseResponse> exercises
) {}
