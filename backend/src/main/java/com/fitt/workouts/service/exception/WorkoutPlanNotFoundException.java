package com.fitt.workouts.service.exception;

public class WorkoutPlanNotFoundException extends RuntimeException {

    public WorkoutPlanNotFoundException(Long planId) {
        super("Workout plan not found: " + planId);
    }
}
