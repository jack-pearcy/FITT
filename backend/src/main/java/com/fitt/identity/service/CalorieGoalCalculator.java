package com.fitt.identity.service;

import com.fitt.identity.domain.GoalDirection;
import org.springframework.stereotype.Service;

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
}