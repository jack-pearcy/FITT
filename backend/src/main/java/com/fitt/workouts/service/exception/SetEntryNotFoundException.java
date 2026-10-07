package com.fitt.workouts.service.exception;

public class SetEntryNotFoundException extends RuntimeException {

    public SetEntryNotFoundException(Long setId) {
        super("Set entry not found: " + setId);
    }
}