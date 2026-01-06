package com.fitt.workouts.service.model;

import java.time.Instant;


// Prevents persistent types from leaking upwards
public record WorkoutView(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}

