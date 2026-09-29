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
class RpeAutoregulationStrategyTest {

    private static final long EXERCISE_ID = 1L;
    private static final long OTHER_EXERCISE_ID = 999L;

    private RpeAutoregulationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RpeAutoregulationStrategy();
    }

    @Test
    void suggest_noSessionHistory_returnsColdStartSuggestion() {
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of()));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("No session history");
    }

    @Test
    void suggest_noSetsLoggedForExercise_returnsNoRpeDataSuggestion() {
        ExerciseEntity otherExercise = exerciseWithId(OTHER_EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(otherExercise, new BigDecimal("100"), 8, new BigDecimal("8.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("No RPE data");
    }

    @Test
    void suggest_setsWithoutRpe_returnsNoRpeDataSuggestion() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, null, 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        // Exercise matches but every RPE is null. RPE filter empties the stream.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("No RPE data");
    }

    @Test
    void suggest_avgRpeBelowTarget_increasesWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, new BigDecimal("5.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        // avgRpe 5.0 below 7.0 floor. +2.5, new weight 102.5.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("below");
    }

    @Test
    void suggest_avgRpeAtLowerBoundary_holdsWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, new BigDecimal("7.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        // avgRpe 7.0. Check is < not <=, holds.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("100");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("target zone");
    }

    @Test
    void suggest_avgRpeAtUpperBoundary_holdsWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, new BigDecimal("8.5"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        // avgRpe 8.5. Check is > not >=. Hold.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("100");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("target zone");
    }

    @Test
    void suggest_avgRpeAboveTarget_decreasesWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, new BigDecimal("10.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        // avgRpe 10.0 above 8.5 ceiling. -2.5, new weight 97.5.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("97.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
        assertThat(suggestion.reasoning()).contains("above");
    }

    // JPA assigns id on save. No setter, so reflection.
    private ExerciseEntity exerciseWithId(long id) {
        ExerciseEntity exercise = new ExerciseEntity(null, "Back Squat", "Legs", false);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    // RPE autoregulation only reads exerciseId and recentSessions off the context.
    private ProgressionContext ctx(List<WorkoutSessionEntity> sessions) {
        return new ProgressionContext(EXERCISE_ID, sessions, 0, 0);
    }

    // One session, one exercise entry, N identical sets.
    // Pass null for rpe to exercise the no-RPE data path.
    private WorkoutSessionEntity sessionWith(ExerciseEntity exercise, BigDecimal weight,
                                             int reps, BigDecimal rpe, int setCount) {
        WorkoutSessionEntity session = new WorkoutSessionEntity(1L, Instant.EPOCH);
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, 0);
        for (int setNumber = 1; setNumber <= setCount; setNumber++) {
            entry.addSetEntry(new SetEntryEntity(setNumber, weight, reps, rpe));
        }
        session.addExerciseEntry(entry);
        return session;
    }
}