package com.fitt.identity.service.exception;

// The code for connecting a device is unknown, expired, or already used. One
// message for all three, so the answer does not say which.
public class DeviceLinkException extends RuntimeException {

    public DeviceLinkException() {
        super("Code is not valid, ask for a new one");
    }
}
