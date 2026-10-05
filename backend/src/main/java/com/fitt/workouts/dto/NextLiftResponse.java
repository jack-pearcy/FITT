package com.fitt.workouts.dto;

// Screen shaped payload for the Home and Today's Lift screen. One call returns the
// active plan, the day the rotation points at next with its exercises and targets,
// where the user sits in the periodization cycle, and whether a session was already
// finished today. weekInCycle and deload follow the linear periodization model.
public record NextLiftResponse(
        boolean hasActivePlan,
        Long planId,
        String planName,
        int daysInRotation,
        int dayNumber,
        int weekInCycle,
        boolean deload,
        boolean alreadyTrainedToday,
        WorkoutPlanDayResponse day
) {}