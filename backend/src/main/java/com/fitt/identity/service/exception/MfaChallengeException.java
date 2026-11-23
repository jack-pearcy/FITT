package com.fitt.identity.service.exception;

// The token for the second sign-in step is unknown, expired, or already used.
// The password has to be entered again.
public class MfaChallengeException extends RuntimeException {

    public MfaChallengeException() {
        super("Sign-in step expired, sign in again");
    }
}
