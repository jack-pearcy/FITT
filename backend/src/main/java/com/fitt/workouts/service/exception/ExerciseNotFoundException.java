package com.fitt.workouts.service.exception;

public class ExerciseNotFoundException extends RuntimeException {

    public ExerciseNotFoundException(Long exerciseId) {
        super("Exercise not found: " + exerciseId);
    }
}
