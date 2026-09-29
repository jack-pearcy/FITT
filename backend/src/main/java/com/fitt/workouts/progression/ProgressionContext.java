package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import java.util.List;

public record ProgressionContext(
        Long exerciseId,
        List<WorkoutSessionEntity> recentSessions,
        long completedSessionCount,
        int daysInRotation) {}
