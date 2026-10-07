package com.fitt.workouts.service.exception;

public class ExerciseEntryNotFoundException extends RuntimeException {

    public ExerciseEntryNotFoundException(Long entryId) {
        super("Exercise entry not found: " + entryId);
    }
}
