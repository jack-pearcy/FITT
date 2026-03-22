package com.fitt.workouts.service.exception;

public class WorkoutSessionNotFoundException extends RuntimeException {

    public WorkoutSessionNotFoundException(Long sessionId) {
        super("Workout session not found: " + sessionId);
    }
}
