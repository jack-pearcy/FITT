package com.fitt.identity.service.exception;

// The password sent to confirm a change to the account did not match. The caller
// is already signed in, so this is a mistake in the request and not a failed
// login: it must not read as "your session is over".
public class IncorrectPasswordException extends RuntimeException {

    private final String field;

    // The request field the password arrived in, so the client can show the
    // message beside the right box.
    public IncorrectPasswordException(String field) {
        super("Password is incorrect");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
