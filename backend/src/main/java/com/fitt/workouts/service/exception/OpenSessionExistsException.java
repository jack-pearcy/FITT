package com.fitt.workouts.service.exception;

// A user may have one unfinished session at a time. Carries the open session's id
// so the client can resume it instead of starting another.
public class OpenSessionExistsException extends RuntimeException {

    private final Long openSessionId;

    public OpenSessionExistsException(Long openSessionId) {
        super("A workout is already in progress, finish it before starting another");
        this.openSessionId = openSessionId;
    }

    public Long getOpenSessionId() {
        return openSessionId;
    }
}
