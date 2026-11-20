package com.fitt.coach.service;

import com.fitt.identity.domain.GoalDirection;
import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The prompt builder decides what leaves the server, so these tests pin down
// both what is written and what must never be.
class CoachPromptBuilderTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static WorkoutSessionResponse session(long id, Instant finishedAt, String notes,
                                                  ExerciseEntryResponse... entries) {
        return new WorkoutSessionResponse(id, 987654L, 4321L, finishedAt.minusSeconds(3600), finishedAt, notes,
                List.of(entries));
    }

    private static ExerciseEntryResponse entry(String name, MuscleGroup group, SetEntryResponse... sets) {
        return new ExerciseEntryResponse(555L, 0, new ExerciseResponse(777L, name, group, false), List.of(sets));
    }

    private static SetEntryResponse set(int number, Integer reps, String weight, String rpe, boolean completed) {
        return new SetEntryResponse(888L, number, reps,
                weight == null ? null : new BigDecimal(weight), rpe == null ? null : new BigDecimal(rpe), completed);
    }

    @Test
    void writesTheGoalAndEachCompletedSetInPlainWords() {
        String summary = CoachPromptBuilder.trainingSummary(List.of(
                session(1, NOW.minusSeconds(3 * 86400), null,
                        entry("Bench Press", MuscleGroup.CHEST,
                                set(1, 8, "80.00", "7.5", true),
                                set(2, 6, "82.50", null, true)))),
                GoalDirection.BULK, NOW);

        assertThat(summary).contains("Goal: bulk");
        assertThat(summary).contains("3 days ago");
        assertThat(summary).contains("Bench Press (chest): 80 kg x 8 @ RPE 7.5, 82.5 kg x 6");
    }

    @Test
    void bodyweightSetsAndUnfinishedSetsAreHandled() {
        String summary = CoachPromptBuilder.trainingSummary(List.of(
                session(1, NOW.minusSeconds(60), null,
                        entry("Pull Up", MuscleGroup.BACK,
                                set(1, 10, null, null, true),
                                set(2, 9, null, null, false)),
                        entry("Plank", MuscleGroup.CORE, set(1, 1, null, null, false)))),
                null, NOW);

        assertThat(summary).contains("Goal: not set");
        assertThat(summary).contains("Today");
        // The set that was not completed is left out.
        assertThat(summary).contains("Pull Up (back): 10 reps\n");
        assertThat(summary).contains("Plank (core): no completed sets");
    }

    @Test
    void neverWritesNotesIdsOrExactTimes() {
        String summary = CoachPromptBuilder.trainingSummary(List.of(
                session(424242, NOW.minusSeconds(86400), "left shoulder hurt, call Dr Rivera",
                        entry("Squat", MuscleGroup.LEGS, set(1, 5, "100", "8", true)))),
                GoalDirection.CUT, NOW);

        assertThat(summary).doesNotContain("shoulder").doesNotContain("Rivera");
        // Session id, owner id, plan id, entry id, exercise id, set id.
        assertThat(summary).doesNotContain("424242").doesNotContain("987654").doesNotContain("4321")
                .doesNotContain("555").doesNotContain("777").doesNotContain("888");
        assertThat(summary).doesNotContain("2026").doesNotContain("T12:");
    }
}
