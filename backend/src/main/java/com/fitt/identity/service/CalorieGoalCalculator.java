package com.fitt.identity.service;

import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.RateWarning;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Turns a maintenance calorie number (TDEE) into a daily goal by applying a
// flat offset keyed to the user's goal direction. Flat percentage of TDEE
// rather than a rate based calc, because goal direction is always present
// while target weight and timeline are optional.
//
// The multipliers sit in the novice and intermediate band from the Iraki 2019
// review and match what mainstream evidence based calculators ship: a standard
// bulk at a 15 percent surplus and a moderate cut at a 20 percent deficit.
@Service
public class CalorieGoalCalculator {

    // Plus 15 percent of TDEE, the middle of the 10 to 20 percent surplus band.
    private static final double BULK_MULTIPLIER = 1.15;

    // Minus 20 percent of TDEE, a moderate deficit near 0.5 kg per week.
    private static final double CUT_MULTIPLIER = 0.80;

    public int adjustForGoal(int maintenanceCalories, GoalDirection direction) {
        return switch (direction) {
            case BULK -> (int) Math.round(maintenanceCalories * BULK_MULTIPLIER);
            case CUT -> (int) Math.round(maintenanceCalories * CUT_MULTIPLIER);
            case MAINTAIN -> maintenanceCalories;
        };
    }

    // Energy in one kilogram of body weight change. The usual planning figure
    // (3,500 kcal per pound), good enough for an estimate shown to the user.
    private static final BigDecimal KCAL_PER_KG = BigDecimal.valueOf(7700);

    // Iraki 2019: gain about 0.25 to 0.5 percent of body weight a week.
    private static final BigDecimal MAX_GAIN_PERCENT = new BigDecimal("0.5");

    // Helms 2014: lose about 0.5 to 1 percent of body weight a week.
    private static final BigDecimal MAX_LOSS_PERCENT = new BigDecimal("-1.0");

    // Kilograms a week the goal implies. Above zero is a gain.
    public BigDecimal weeklyRateKg(int calorieGoal, int maintenanceCalories) {
        return unroundedRateKg(calorieGoal, maintenanceCalories).setScale(2, RoundingMode.HALF_UP);
    }

    // The weekly rate as a percent of current body weight, with the same sign.
    public BigDecimal weeklyRatePercent(int calorieGoal, int maintenanceCalories, BigDecimal weightKg) {
        return unroundedRatePercent(calorieGoal, maintenanceCalories, weightKg)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Null when the rate is inside the advised range. Compared before rounding,
    // so a rate exactly at a threshold does not warn.
    public RateWarning rateWarning(int calorieGoal, int maintenanceCalories, BigDecimal weightKg) {
        BigDecimal percent = unroundedRatePercent(calorieGoal, maintenanceCalories, weightKg);
        if (percent.compareTo(MAX_GAIN_PERCENT) > 0) {
            return RateWarning.GAIN_TOO_FAST;
        }
        if (percent.compareTo(MAX_LOSS_PERCENT) < 0) {
            return RateWarning.LOSS_TOO_FAST;
        }
        return null;
    }

    private BigDecimal unroundedRateKg(int calorieGoal, int maintenanceCalories) {
        return BigDecimal.valueOf((long) (calorieGoal - maintenanceCalories) * 7)
                .divide(KCAL_PER_KG, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal unroundedRatePercent(int calorieGoal, int maintenanceCalories, BigDecimal weightKg) {
        return unroundedRateKg(calorieGoal, maintenanceCalories)
                .multiply(BigDecimal.valueOf(100))
                .divide(weightKg, 8, RoundingMode.HALF_UP);
    }
}
