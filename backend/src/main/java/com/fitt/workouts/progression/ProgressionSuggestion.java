package com.fitt.workouts.progression;

import java.math.BigDecimal;

// What the algorithm recommends for the next session on a specific exercise.
// Internal type consumed by the service layer, not exposed directly over the API.
// reasoning is the English text older clients show. reasonCode and the numbers after
// it carry the same decision in a form a client can format in its own units.
// previousWeightKg, averageRpe and weekInCycle are null when the branch did not use them.
public record ProgressionSuggestion(
        Long exerciseId,
        BigDecimal suggestedWeight,
        Integer suggestedReps,
        Integer suggestedSets,
        String reasoning,
        SuggestionReason reasonCode,
        BigDecimal previousWeightKg,
        Integer targetReps,
        BigDecimal averageRpe,
        Integer weekInCycle
) {}
