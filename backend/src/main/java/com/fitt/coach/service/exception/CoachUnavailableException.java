package com.fitt.coach.service.exception;

// The coach is switched off on this server: no API key is configured.
public class CoachUnavailableException extends RuntimeException {
    public CoachUnavailableException(String message) {
        super(message);
    }
}
