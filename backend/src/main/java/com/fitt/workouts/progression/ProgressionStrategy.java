package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;

import java.util.List;

// Strategy interface for the progression engine.
// Each implementation encapsulates one evidence-backed progression algorithm.
// Adding a new model = one new class implementing this, zero changes to existing code.
public interface ProgressionStrategy {

    // Analyze recent sessions for a specific exercise and return a suggestion
    ProgressionSuggestion suggest(Long exerciseId, List<WorkoutSessionEntity> recentSessions);
}
