package com.fitt.identity.service.exception;

// The request does not fit the account's current state: turning on what is
// already on, or confirming or turning off what was never set up.
public class MfaStateException extends RuntimeException {

    public MfaStateException(String message) {
        super(message);
    }
}
