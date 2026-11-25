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

    @Test
    void suggest_noSessionHistory_setsNoHistoryReasonAndKeepsText() {
        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of()));

        assertThat(suggestion.reasonCode()).isEqualTo(SuggestionReason.NO_HISTORY);
        assertThat(suggestion.previousWeightKg()).isNull();
        assertThat(suggestion.targetReps()).isEqualTo(8);
        assertThat(suggestion.averageRpe()).isNull();
        assertThat(suggestion.weekInCycle()).isNull();
        assertThat(suggestion.reasoning()).isEqualTo(
                "No session history. Start light and rate each set using RPE.");
    }

    @Test
    void suggest_setsWithoutRpe_setsNoRpeDataReasonAndKeepsText() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, null, 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        assertThat(suggestion.reasonCode()).isEqualTo(SuggestionReason.NO_RPE_DATA);
        assertThat(suggestion.previousWeightKg()).isNull();
        assertThat(suggestion.targetReps()).isEqualTo(8);
        assertThat(suggestion.averageRpe()).isNull();
        assertThat(suggestion.weekInCycle()).isNull();
        assertThat(suggestion.reasoning()).isEqualTo(
                "No RPE data found. Log RPE on your sets to enable autoregulation.");
    }

    @Test
    void suggest_avgRpeBelowTarget_setsRpeLowReasonAndKeepsText() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 9, new BigDecimal("5.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        assertThat(suggestion.reasonCode()).isEqualTo(SuggestionReason.RPE_LOW);
        assertThat(suggestion.previousWeightKg()).isEqualByComparingTo("100");
        assertThat(suggestion.targetReps()).isEqualTo(9);
        assertThat(suggestion.averageRpe()).isEqualByComparingTo("5.0");
        assertThat(suggestion.weekInCycle()).isNull();
        assertThat(suggestion.reasoning()).isEqualTo(
                "Average RPE was 5.0 which is below the target zone (7.0 to 8.5). "
                        + "Increase weight to 102.5.");
    }

    @Test
    void suggest_avgRpeAboveTarget_setsRpeHighReasonAndKeepsText() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 6, new BigDecimal("9.5"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        assertThat(suggestion.reasonCode()).isEqualTo(SuggestionReason.RPE_HIGH);
        assertThat(suggestion.previousWeightKg()).isEqualByComparingTo("100");
        assertThat(suggestion.targetReps()).isEqualTo(6);
        assertThat(suggestion.averageRpe()).isEqualByComparingTo("9.5");
        assertThat(suggestion.weekInCycle()).isNull();
        assertThat(suggestion.reasoning()).isEqualTo(
                "Average RPE was 9.5 which is above the target zone. "
                        + "Consider reducing weight to 97.5 to stay in the productive training range.");
    }

    @Test
    void suggest_avgRpeInTarget_setsHoldReasonAndKeepsText() {
        ExerciseEntity squat = exerciseWithId(EXERCISE_ID);
        WorkoutSessionEntity session = sessionWith(squat, new BigDecimal("100"), 8, new BigDecimal("8.0"), 3);

        ProgressionSuggestion suggestion = strategy.suggest(ctx(List.of(session)));

        assertThat(suggestion.reasonCode()).isEqualTo(SuggestionReason.HOLD);
        assertThat(suggestion.previousWeightKg()).isEqualByComparingTo("100");
        assertThat(suggestion.targetReps()).isEqualTo(8);
        assertThat(suggestion.averageRpe()).isEqualByComparingTo("8.0");
        assertThat(suggestion.weekInCycle()).isNull();
        assertThat(suggestion.reasoning()).isEqualTo(
                "Average RPE was 8.0 which is right in the target zone. "
                        + "Keep the current weight and focus on quality reps.");
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