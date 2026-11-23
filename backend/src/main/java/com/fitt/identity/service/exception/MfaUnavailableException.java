package com.fitt.identity.service.exception;

// Two-step sign-in is switched off on this server: no encryption key is set.
public class MfaUnavailableException extends RuntimeException {

    public MfaUnavailableException() {
        super("Two-step sign-in is not available right now");
    }
}
