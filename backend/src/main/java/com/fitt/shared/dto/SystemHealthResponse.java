package com.fitt.shared.dto;

import java.time.Instant;

public class SystemHealthResponse {

    private final String status;
    private final Instant timestamp;
    private final String application;

    public SystemHealthResponse(String status, Instant timestamp, String application) {
        this.status = status;
        this.timestamp = timestamp;
        this.application = application;
    }

    public String getStatus() {
        return status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getApplication() {
        return application;
    }
}

