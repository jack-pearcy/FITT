package com.fitt.identity.service.exception;

// A goal request whose fields are each in range but do not work together.
// Carries the field name so the client can show the message beside it.
public class InvalidGoalException extends RuntimeException {

    private final String field;

    public InvalidGoalException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
