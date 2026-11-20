package com.fitt.coach.service.exception;

// The user has had every review the daily limit allows.
public class CoachLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public CoachLimitException(long retryAfterSeconds) {
        super("You have reached today's limit for coach reviews");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
