package com.fitt.workouts.dto;

import com.fitt.workouts.domain.SplitType;
import java.util.List;

public record WorkoutPlanResponse(
        Long id,
        Long ownerUserId,
        String name,
        int daysPerWeek,
        SplitType splitType,
        boolean active,
        List<WorkoutPlanDayResponse> days
) {}
