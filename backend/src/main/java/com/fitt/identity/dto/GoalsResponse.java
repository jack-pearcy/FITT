package com.fitt.identity.dto;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.RateWarning;

import java.math.BigDecimal;

// Read only DTO for the Goals tab. Separate from SaveGoalsRequest
// because read and write shapes change over time.
//
// maintenanceKcal is the user's own number when maintenanceIsUserSet is true,
// else the estimate from their body measures. adjustmentKcal is the saved daily
// surplus or deficit, null when the user set none. weeklyRateKg is signed:
// above zero is a gain. weeklyRatePercent is that rate as a share of current
// body weight. rateWarning is null when the rate is inside the advised range.
public record GoalsResponse(
        GoalDirection goalDirection,
        BigDecimal targetWeightKg,
        Integer timelineWeeks,
        CalorieGoalMode calorieGoalMode,
        Integer calorieGoal,
        Integer maintenanceKcal,
        boolean maintenanceIsUserSet,
        Integer adjustmentKcal,
        BigDecimal weeklyRateKg,
        BigDecimal weeklyRatePercent,
        RateWarning rateWarning
) {}
