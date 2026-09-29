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
// The week in the cycle is driven by completed sessions divided by rotation length,
// so each test sets a completed-session count that lands on the week it is checking.
// A 3-day split is used throughout, so 3 completed sessions is one full rotation.
class LinearPeriodizationStrategyTest {

    private static final long EXERCISE_ID = 1L;
    private static final long OTHER_EXERCISE_ID = 999L;
    private static final int DAYS_IN_ROTATION = 3;

    private LinearPeriodizationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LinearPeriodizationStrategy();
    }

    @Test
    void suggest_noSessionHistory_returnsColdStartSuggestion() {
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(), 0));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.reasoning()).contains("No session history");
    }

    @Test
    void suggest_noSetsLoggedForExercise_returnsNoCompletedSetsSuggestion() {
        ExerciseEntity otherExercise = exerciseWithId(OTHER_EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(otherExercise, new BigDecimal("100"), 10, 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 3));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("0");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.reasoning()).contains("No completed sets");
    }

    @Test
    void suggest_startOfCycle_weekOne_increasesWeight() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 0 completed sessions = 0 rotations = week 1. 100 * 0.025 = 2.5 -> 102.5, reps 10.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 0));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
    }

    @Test
    void suggest_oneRotationComplete_weekTwo() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 3 completed sessions / 3 days = 1 rotation = week 2. +2.5 -> 102.5, reps 8.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 3));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(8);
    }

    @Test
    void suggest_twoRotationsComplete_weekThree() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 6 completed / 3 = 2 rotations = week 3. reps 6.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 6));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(6);
    }

    @Test
    void suggest_threeRotationsComplete_triggersDeload() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 9 completed / 3 = 3 rotations = week 4 = deload. 100 * 0.6 = 60, reps 12.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 9));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("60");
        assertThat(suggestion.suggestedReps()).isEqualTo(12);
        assertThat(suggestion.reasoning()).contains("Deload");
    }

    @Test
    void suggest_fourRotationsComplete_cyclesBackToWeekOne() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 12 completed / 3 = 4 rotations, 4 % 4 = 0, back to week 1. reps 10.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 12));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
        assertThat(suggestion.suggestedSets()).isEqualTo(3);
    }

    @Test
    void suggest_manyCompletedSessions_doesNotFreeze() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        // Only one recent session for the weight, but the plan has many completed sessions.
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // 9 completed / 3 = 3 rotations = week 4 = deload. This is the regression guard.
        // The old code derived the week from recentSessions.size(), which was capped at 5,
        // so a single recent session read as week 2 and the cycle froze. Driving the week
        // from the true completed count lands correctly on the deload.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 9));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("60");
        assertThat(suggestion.suggestedReps()).isEqualTo(12);
        assertThat(suggestion.reasoning()).contains("Deload");
    }

    @Test
    void suggest_zeroDaysInRotation_defaultsToWeekOneWithoutDividing() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 10, 3);

        // Malformed plan with no days. The guard avoids divide-by-zero and reads as week 1.
        ProgressionContext context =
                new ProgressionContext(EXERCISE_ID, List.of(session), 5, 0);
        ProgressionSuggestion suggestion = strategy.suggest(context);

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("102.5");
        assertThat(suggestion.suggestedReps()).isEqualTo(10);
    }

    @Test
    void suggest_percentageIncreaseAtMidpointTie_roundsHalfUp() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("102"), 10, 3);

        // Week 1 increase. 102 * 0.025 = 2.550, exact tie, HALF_UP -> 2.6, total 104.6.
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session), 0));

        assertThat(suggestion.suggestedWeight()).isEqualByComparingTo("104.6");
    }

    // Builds a context for the standard 3-day split with the given completed-session count.
    private ProgressionContext ctx(List<WorkoutSessionEntity> sessions, long completedSessionCount) {
        return new ProgressionContext(EXERCISE_ID, sessions, completedSessionCount, DAYS_IN_ROTATION);
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