package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

// Double progression: keep weight constant, add reps until top of range,
// then bump weight and reset to bottom of range.
// Based on the ACSM position stand (2009) and Chaves et al. (2024).
@Component("DOUBLE_PROGRESSION")
public class DoubleProgressionStrategy implements ProgressionStrategy {

    private static final int REP_RANGE_BOTTOM = 8;
    private static final int REP_RANGE_TOP = 12;
    private static final BigDecimal WEIGHT_INCREMENT = new BigDecimal("2.5");
    private static final int SESSIONS_TO_CHECK = 2;

    @Override
    public ProgressionSuggestion suggest(Long exerciseId, List<WorkoutSessionEntity> recentSessions) {
        if (recentSessions.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, REP_RANGE_BOTTOM, 3,
                    "No session history found. Start with a comfortable weight for "
                            + REP_RANGE_BOTTOM + " reps.");
        }

        WorkoutSessionEntity latestSession = recentSessions.getFirst();
        List<SetEntryEntity> latestSets = extractSetsForExercise(latestSession, exerciseId);

        if (latestSets.isEmpty()) {
            return new ProgressionSuggestion(
                    exerciseId, BigDecimal.ZERO, REP_RANGE_BOTTOM, 3,
                    "No completed sets found for this exercise in recent sessions.");
        }

        BigDecimal lastWeight = findLastWeight(latestSets);
        int setCount = latestSets.size();

        // The core rule: did the user hit the rep ceiling on ALL sets
        // for the required number of consecutive sessions?
        boolean readyToProgress = recentSessions.size() >= SESSIONS_TO_CHECK
                && sessionsAllHitTarget(recentSessions.subList(0, SESSIONS_TO_CHECK), exerciseId);

        if (readyToProgress) {
            BigDecimal newWeight = lastWeight.add(WEIGHT_INCREMENT);
            return new ProgressionSuggestion(
                    exerciseId, newWeight, REP_RANGE_BOTTOM, setCount,
                    "You hit " + REP_RANGE_TOP + " reps on all sets for "
                            + SESSIONS_TO_CHECK + " sessions in a row. "
                            + "Increase weight to " + newWeight + " and reset to "
                            + REP_RANGE_BOTTOM + " reps.");
        }

        return new ProgressionSuggestion(
                exerciseId, lastWeight, REP_RANGE_TOP, setCount,
                "Keep " + lastWeight + " and work toward " + REP_RANGE_TOP
                        + " reps on every set before increasing weight.");
    }

    private boolean sessionsAllHitTarget(List<WorkoutSessionEntity> sessions, Long exerciseId) {
        for (WorkoutSessionEntity session : sessions) {
            List<SetEntryEntity> sets = extractSetsForExercise(session, exerciseId);
            boolean allHit = !sets.isEmpty() && sets.stream()
                    .allMatch(s -> s.getReps() != null
                            && s.getReps() >= REP_RANGE_TOP);
            if (!allHit) return false;
        }
        return true;
    }

    private List<SetEntryEntity> extractSetsForExercise(WorkoutSessionEntity session, Long exerciseId) {
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