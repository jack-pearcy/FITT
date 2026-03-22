package com.fitt.workouts.dto;

import java.time.Instant;
import java.util.List;

public record WorkoutSessionResponse(
        Long id,
        Long ownerUserId,
        Instant startedAt,
        Instant finishedAt,
        String notes,
        List<ExerciseEntryResponse> exerciseEntries
) {}