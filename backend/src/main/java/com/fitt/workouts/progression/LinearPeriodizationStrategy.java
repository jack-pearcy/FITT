package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Linear periodization: increase weight week over week with a deload on week 4.
// Based on the ACSM position stand (2009) classical periodization model.
// A "week" here is one full rotation of the plan's split, not one session, so the
// cycle advances on completed rotations and tracks accumulated training, not the calendar.
@Component("LINEAR_PERIODIZATION")
public class LinearPeriodizationStrategy implements ProgressionStrategy {

    private static final BigDecimal WEEKLY_INCREASE_PERCENT = new BigDecimal("0.025");
    private static final BigDecimal DELOAD_RATIO = new BigDecimal("0.6");
    private static final int CYCLE_LENGTH = 4;

    @Override
    public ProgressionSuggestion suggest(ProgressionContext context) {
        Long exerciseId = context.exerciseId();
        List<WorkoutSessionEntity> recentSessions = context.recentSessions();
        long completedSessionCount = context.completedSessionCount();
        int daysInRotation = context.daysInRotation();

        if (recentSessions.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, 10, 3,
                    "No session history. Start with a weight you can control for 10 reps.");
        }

        WorkoutSessionEntity latestSession = recentSessions.getFirst();
        List<SetEntryEntity> sets = extractCompletedSets(latestSession, exerciseId);

        if (sets.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, 10, 3,
                    "No completed sets for this exercise in recent sessions.");
        }

        BigDecimal lastWeight = findLastWeight(sets);
        int setCount = sets.size();

        // One full rotation of the split is one week of the cycle. Total completed
        // sessions divided by the rotation length gives rotations done, wrapping that
        // by the cycle length gives the current week. Guard against a zero rotation
        // length so a malformed plan cannot divide by zero.
        long rotationsCompleted = daysInRotation > 0 ? completedSessionCount / daysInRotation : 0;
        int weekInCycle = (int) (rotationsCompleted % CYCLE_LENGTH) + 1;

        if (weekInCycle == CYCLE_LENGTH) {
            BigDecimal deloadWeight = lastWeight.multiply(DELOAD_RATIO)
                    .setScale(1, RoundingMode.HALF_UP);
            return new ProgressionSuggestion(
                    exerciseId, deloadWeight, 12, setCount,
                    "Deload week. Reduce to " + deloadWeight
                            + " at higher reps to recover before the next cycle.");
        }

        BigDecimal increase = lastWeight.multiply(WEEKLY_INCREASE_PERCENT)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal newWeight = lastWeight.add(increase);

        // Reps decrease as intensity increases across the loading weeks
        int targetReps = switch (weekInCycle) {
            case 1 -> 10;
            case 2 -> 8;
            case 3 -> 6;
            default -> 10;
        };

        return new ProgressionSuggestion(
                exerciseId, newWeight, targetReps, setCount,
                "Week " + weekInCycle + " of " + CYCLE_LENGTH + ". "
                        + "Increase weight to " + newWeight + " for " + targetReps + " reps.");
    }

    private List<SetEntryEntity> extractCompletedSets(WorkoutSessionEntity session, Long exerciseId) {
        return session.getExerciseEntries().stream()
                .filter(e -> e.getExercise().getId().equals(exerciseId))
                .flatMap(e -> e.getSetEntries().stream())
                .filter(s -> s.getReps() != null && s.getWeight() != null)
                .toList();
    }

    private BigDecimal findLastWeight(List<SetEntryEntity> sets) {
        return sets.stream()
                .map(SetEntryEntity::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .reduce((first, second) -> second)
                .orElse(BigDecimal.ZERO);
    }
}