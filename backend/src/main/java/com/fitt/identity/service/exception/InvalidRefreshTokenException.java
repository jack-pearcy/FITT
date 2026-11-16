package com.fitt.identity.service.exception;

// The refresh token is unknown, expired, or already used. The message stays the
// same for all three so a caller cannot learn which tokens once existed.
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
