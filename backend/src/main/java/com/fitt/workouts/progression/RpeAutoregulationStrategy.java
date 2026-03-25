package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// RPE autoregulation: adjust weight based on reported RPE scores.
// Based on Zourdos et al. (2016) and Helms et al. (2018).
@Component("RPE_AUTOREGULATION")
public class RpeAutoregulationStrategy implements ProgressionStrategy {

    private static final BigDecimal TARGET_RPE_LOW = new BigDecimal("7.0");
    private static final BigDecimal TARGET_RPE_HIGH = new BigDecimal("8.5");
    private static final BigDecimal WEIGHT_ADJUSTMENT_PERCENT = new BigDecimal("0.025");

    @Override
    public ProgressionSuggestion suggest(Long exerciseId, List<WorkoutSessionEntity> recentSessions) {
        if (recentSessions.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, 8, 3,
                    "No session history. Start light and rate each set using RPE.");
        }

        WorkoutSessionEntity latestSession = recentSessions.getFirst();
        List<SetEntryEntity> sets = extractCompletedSetsWithRpe(latestSession, exerciseId);

        if (sets.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, 8, 3,
                    "No RPE data found. Log RPE on your sets to enable autoregulation.");
        }

        BigDecimal avgRpe = calculateAverageRpe(sets);
        BigDecimal lastWeight = findLastWeight(sets);
        int lastReps = findLastReps(sets);
        int setCount = sets.size();

        if (avgRpe.compareTo(TARGET_RPE_LOW) < 0) {
            BigDecimal increase = lastWeight.multiply(WEIGHT_ADJUSTMENT_PERCENT)
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal newWeight = lastWeight.add(increase);
            return new ProgressionSuggestion(
                    exerciseId, newWeight, lastReps, setCount,
                    "Average RPE was " + avgRpe + " which is below the target zone ("
                            + TARGET_RPE_LOW + " to " + TARGET_RPE_HIGH
                            + "). Increase weight to " + newWeight + ".");
        }

        if (avgRpe.compareTo(TARGET_RPE_HIGH) > 0) {
            BigDecimal decrease = lastWeight.multiply(WEIGHT_ADJUSTMENT_PERCENT)
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal newWeight = lastWeight.subtract(decrease);
            return new ProgressionSuggestion(
                    exerciseId, newWeight, lastReps, setCount,
                    "Average RPE was " + avgRpe + " which is above the target zone. "
                            + "Consider reducing weight to " + newWeight
                            + " to stay in the productive training range.");
        }

        return new ProgressionSuggestion(
                exerciseId, lastWeight, lastReps, setCount,
                "Average RPE was " + avgRpe + " which is right in the target zone. "
                        + "Keep the current weight and focus on quality reps.");
    }

    private List<SetEntryEntity> extractCompletedSetsWithRpe(WorkoutSessionEntity session, Long exerciseId) {
        return session.getExerciseEntries().stream()
                .filter(e -> e.getExercise().getId().equals(exerciseId))
                .flatMap(e -> e.getSetEntries().stream())
                .filter(s -> s.getRpe() != null && s.getWeight() != null)
                .toList();
    }

    private BigDecimal calculateAverageRpe(List<SetEntryEntity> sets) {
        BigDecimal total = sets.stream()
                .map(SetEntryEntity::getRpe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(sets.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal findLastWeight(List<SetEntryEntity> sets) {
        return sets.stream()
                .map(SetEntryEntity::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .reduce((first, second) -> second)
                .orElse(BigDecimal.ZERO);
    }

    private int findLastReps(List<SetEntryEntity> sets) {
        return sets.stream()
                .map(SetEntryEntity::getReps)
                .filter(r -> r != null && r > 0)
                .reduce((first, second) -> second)
                .orElse(8);
    }
}