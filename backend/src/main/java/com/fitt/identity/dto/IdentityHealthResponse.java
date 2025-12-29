package com.fitt.identity.dto;

import java.time.Instant;

public class IdentityHealthResponse {

    private final String status;
    private final Instant timestamp;
    private final String provider;

    public IdentityHealthResponse(String status, Instant timestamp, String provider) {
        this.status = status;
        this.timestamp = timestamp;
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getProvider() {
        return provider;
    }
}

