package com.fitt.identity.dto;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveGoalsRequest(
        @NotNull GoalDirection goalDirection,
        BigDecimal targetWeightKg,
        Integer timelineWeeks,
        @NotNull CalorieGoalMode calorieGoalMode,
        Integer calorieGoal
) {}