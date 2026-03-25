package com.fitt.workouts.dto;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.progression.ProgressionSuggestion;

public record ProgressionSuggestionResponse(
        Long exerciseId,
        ProgressionModel modelUsed,
        String suggestedWeight,
        Integer suggestedReps,
        Integer suggestedSets,
        String reasoning
) {
    public static ProgressionSuggestionResponse from(ProgressionSuggestion suggestion, ProgressionModel model) {
        return new ProgressionSuggestionResponse(
                suggestion.exerciseId(),
                model,
                suggestion.suggestedWeight().toPlainString(),
                suggestion.suggestedReps(),
                suggestion.suggestedSets(),
                suggestion.reasoning());
    }
}
