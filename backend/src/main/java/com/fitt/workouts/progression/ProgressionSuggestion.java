package com.fitt.workouts.progression;

import java.math.BigDecimal;

// What the algorithm recommends for the next session on a specific exercise.
// Internal type consumed by the service layer, not exposed directly over the API.
public record ProgressionSuggestion(
        Long exerciseId,
        BigDecimal suggestedWeight,
        Integer suggestedReps,
        Integer suggestedSets,
        String reasoning
) {}
