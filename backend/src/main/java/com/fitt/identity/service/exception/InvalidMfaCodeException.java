package com.fitt.identity.service.exception;

// The six-digit code or recovery code was wrong, already used, or out of date.
// One message for all of them, so a caller cannot tell a near miss from a wild guess.
public class InvalidMfaCodeException extends RuntimeException {

    public InvalidMfaCodeException() {
        super("Code is incorrect");
    }
}
