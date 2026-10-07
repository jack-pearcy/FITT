package com.fitt.workouts.service.exception;

public class ExerciseInUseException extends RuntimeException {

    public ExerciseInUseException(String message) {
        super(message);
    }
}