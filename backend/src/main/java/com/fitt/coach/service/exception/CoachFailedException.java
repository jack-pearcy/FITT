package com.fitt.coach.service.exception;

// The model was asked and gave nothing usable: an error, a refusal, or no text.
public class CoachFailedException extends RuntimeException {
    public CoachFailedException(String message) {
        super(message);
    }

    public CoachFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
