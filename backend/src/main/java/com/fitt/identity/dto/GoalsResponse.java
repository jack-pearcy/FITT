package com.fitt.identity.dto;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;

import java.math.BigDecimal;

// Read only DTO for the Goals tab. Separate from SaveGoalsRequest
// because read and write shapes change over time.
public record GoalsResponse(
        GoalDirection goalDirection,
        BigDecimal targetWeightKg,
        Integer timelineWeeks,
        CalorieGoalMode calorieGoalMode,
        Integer calorieGoal
) {}