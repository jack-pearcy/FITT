package com.fitt.workouts.dto;

import java.time.Instant;

public class WorkoutResponse {

    private final Long id;
    private final String name;
    private final Instant createdAt;
    private final Instant updatedAt;

    public WorkoutResponse(Long id, String name, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
