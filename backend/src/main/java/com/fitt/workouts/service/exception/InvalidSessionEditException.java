package com.fitt.workouts.service.exception;

// An edit to a session whose fields are each well formed but do not fit the
// session: a finish before the start, a time in the future, or a finish time
// on a workout still in progress. Carries the field so the client can show the
// message beside it.
public class InvalidSessionEditException extends RuntimeException {

    private final String field;

    public InvalidSessionEditException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
