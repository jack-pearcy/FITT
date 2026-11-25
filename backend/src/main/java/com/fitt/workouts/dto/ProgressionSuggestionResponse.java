package com.fitt.workouts.dto;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.progression.ProgressionSuggestion;
import com.fitt.workouts.progression.SuggestionReason;

import java.math.BigDecimal;

// Decimals go out as plain strings, the same way suggestedWeight always has.
// The fields after reasoning were added later and older clients ignore them.
public record ProgressionSuggestionResponse(
        Long exerciseId,
        ProgressionModel modelUsed,
        String suggestedWeight,
        Integer suggestedReps,
        Integer suggestedSets,
        String reasoning,
        SuggestionReason reasonCode,
        String previousWeightKg,
        Integer targetReps,
        String averageRpe,
        Integer weekInCycle
) {
    public static ProgressionSuggestionResponse from(ProgressionSuggestion suggestion, ProgressionModel model) {
        return new ProgressionSuggestionResponse(
                suggestion.exerciseId(),
                model,
                suggestion.suggestedWeight().toPlainString(),
                suggestion.suggestedReps(),
                suggestion.suggestedSets(),
                suggestion.reasoning(),
                suggestion.reasonCode(),
                plain(suggestion.previousWeightKg()),
                suggestion.targetReps(),
                plain(suggestion.averageRpe()),
                suggestion.weekInCycle());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
