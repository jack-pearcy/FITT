package com.fitt.identity.service.exception;

// The password sent to confirm a change to the account did not match. The caller
// is already signed in, so this is a mistake in the request and not a failed
// login: it must not read as "your session is over".
public class IncorrectPasswordException extends RuntimeException {

    public IncorrectPasswordException() {
        super("Password is incorrect");
    }
}
