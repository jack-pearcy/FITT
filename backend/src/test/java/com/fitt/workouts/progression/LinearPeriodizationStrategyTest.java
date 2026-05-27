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
// Strategy has no dependencies so we use it directly.
class LinearPeriodizationStrategyTest {

    private static final long EXERCISE_ID = 1L;
    private static final long OTHER_EXERCISE_ID = 999L;

    private LinearPeriodizationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LinearPeriodizationStrategy();
    }

    @Test
    void suggest_noSessionHistory_returnsColdStartSuggestion() {
        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of());

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.reasoning()).contains("No session history");
    }

    @Test
    void suggest_noSetsLoggedForExercise_returnsNoCompletedSetsSuggestion() {
        ExerciseEntity otherExercise = exerciseWithId(OTHER_EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(otherExercise, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of(session));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.reasoning()).contains("No completed sets");
    }

    @Test
    void suggest_oneSession_advancesToWeekTwoAndIncreasesWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of(session));

        // 1 session = week 2.
        // 100 * 0.025 = 2.5, new weight = 102.5, reps = 8.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
    }

    @Test
    void suggest_twoSessions_advancesToWeekThreeAtLowerReps() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity recent = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity previous = sessionWith(squat, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion =
                strategy.suggest(EXERCISE_ID, List.of(recent, previous));

        // 2 sessions = week 3. Same +2.5 but reps = 6.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(6);
    }

    @Test
    void suggest_threeSessions_triggersDeloadAtSixtyPercent() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity s1 = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity s2 = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity s3 = sessionWith(squat, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion =
                strategy.suggest(EXERCISE_ID, List.of(s1, s2, s3));

        // 3 sessions = week 4 = deload.
        // 100 * 0.6 = 60, reps = 12.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("60");
        assertThat(suggestion.suggestedReps()).isEqualTo(12);
        assertThat(suggestion.reasoning()).contains("Deload");
    }

    @Test
    void suggest_fourSessions_cyclesBackToWeekOne() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity s1 = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity s2 = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity s3 = sessionWith(squat, new BigDecimal("100"), 10, 3);
        WorkoutSessionEntity s4 = sessionWith(squat, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion =
                strategy.suggest(EXERCISE_ID, List.of(s1, s2, s3, s4));

        // 4 % 4 = 0, so week wraps back to 1. Reps = 10.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.suggestedSets()).isEqualTo(3);
    }

    @Test
    void suggest_percentageIncreaseAtMidpointTie_roundsHalfUp() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("102"), 10, 3);

        ProgressionSuggestion suggestion = strategy.suggest(EXERCISE_ID, List.of(session));

        // 102 * 0.025 = 2.550. Exact tie at the rounding point.
        // HALF_UP rounds up to 2.6. 102 + 2.6 = 104.6.
        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("104.6");
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