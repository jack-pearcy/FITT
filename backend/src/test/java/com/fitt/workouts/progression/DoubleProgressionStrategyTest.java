package com.fitt.workouts.progression;

import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test. No Spring, no DB, no mocks.
// Strategy has no dependencies so use it directly.
class DoubleProgressionStrategyTest {

    private static final long EXERCISE_ID = 1L;
    private static final long OTHER_EXERCISE_ID = 999L;

    private DoubleProgressionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DoubleProgressionStrategy();
    }

    @Test
    void suggest_noSessionHistory_returnsColdStartSuggestion() {
        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of());

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("No session history");
    }

    @Test
    void suggest_noSetsLoggedForExercise_returnsNoCompletedSetsSuggestion() {
        ExerciseEntity otherExercise = exerciseWithId(OTHER_EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(otherExercise, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of(session));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("No completed sets");
    }

    @Test
    void suggest_singleSessionAtTopReps_holdsWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 12, 3);

        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of(session));

        // 1 session only. Need 2 to progress. Reps at 12 but weight holds.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("100");
        assertThat(suggestion.suggestedReps()).isEqualTo(12);
    }

    @Test
    void suggest_twoSessionsBelowTopReps_holdsWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity recent = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity previous = sessionWith(squat, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion =
                strategy.suggest(EXERCISE_ID, List.of(recent, previous));

        // 2 sessions but reps below 12. Holds.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("100");
        assertThat(suggestion.suggestedReps()).isEqualTo(12);
    }

    @Test
    void suggest_twoSessionsAtTopReps_increasesWeightByIncrement() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity recent = sessionWith(squat, new BigDecimal("100"), 12, 3);
        WorkoutSessionEntity previous = sessionWith(squat, new BigDecimal("100"), 12, 3);

        ProgressionSuggestion suggestion =
                strategy.suggest(EXERCISE_ID, List.of(recent, previous));

        // 2 sessions, all sets at 12 reps. Progress: +2.5, reset to 8 reps.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.suggestedSets()).isEqualTo(3);
    }

    // JPA assigns id on save. No setter, so reflection.
    private ExerciseEntity exerciseWithId(long id) {
        ExerciseEntity exercise = new ExerciseEntity(null, "Back Squat", "Legs", false);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    // One session, one exercise entry, N identical sets.
    private WorkoutSessionEntity sessionWith(ExerciseEntity exercise, BigDecimal weight,
                                             int reps, int setCount) {
        WorkoutSessionEntity session = new WorkoutSessionEntity(1L, Instant.EPOCH);
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, 0);
        for (int setNumber = 1; setNumber <= setCount; setNumber++) {
            entry.addSetEntry(new SetEntryEntity(setNumber, weight, reps, null));
        }
        session.addExerciseEntry(entry);
        return session;
    }
}