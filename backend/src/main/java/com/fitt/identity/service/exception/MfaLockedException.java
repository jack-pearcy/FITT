package com.fitt.identity.service.exception;

// Too many wrong codes in a row. No code is accepted until the wait is over,
// which is what makes six digits hard to guess.
public class MfaLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public MfaLockedException(long retryAfterSeconds) {
        super("Too many wrong codes, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
