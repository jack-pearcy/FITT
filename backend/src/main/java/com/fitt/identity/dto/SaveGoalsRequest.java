package com.fitt.identity.dto;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveGoalsRequest(
        @NotNull GoalDirection goalDirection,

        @DecimalMin(value = "20", message = "Target weight must be at least 20 kg")
        @DecimalMax(value = "700", message = "Target weight must be 700 kg or less")
        @Digits(integer = 9, fraction = 2, message = "Target weight can have at most 2 decimal places")
        BigDecimal targetWeightKg,

        @Min(value = 1, message = "Timeline must be at least 1 week")
        @Max(value = 520, message = "Timeline must be 520 weeks or fewer")
        Integer timelineWeeks,

        @NotNull CalorieGoalMode calorieGoalMode,

        @Min(value = 500, message = "Calorie goal must be at least 500")
        @Max(value = 10000, message = "Calorie goal must be 10000 or fewer")
        Integer calorieGoal,

        // Optional. The user's own maintenance, used in place of the estimate.
        @Min(value = 1000, message = "Maintenance must be at least 1000")
        @Max(value = 8000, message = "Maintenance must be 8000 or fewer")
        Integer maintenanceOverrideKcal,

        // Optional. Signed daily surplus or deficit, used in AUTO mode in place
        // of the built-in bulk or cut percentage.
        @Min(value = -1500, message = "Adjustment must be -1500 or more")
        @Max(value = 1500, message = "Adjustment must be 1500 or less")
        Integer adjustmentKcal
) {}
