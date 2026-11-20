package com.fitt.coach.service;

import com.fitt.identity.domain.GoalDirection;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

// Turns training data into the text sent to the model. This class is the privacy
// boundary of the coach: whatever it does not write never leaves the server.
//
// Sent: exercise names and muscle groups, sets (weight, reps, RPE), how many days
// ago each session was, and the goal direction.
// Never sent: email, user id, any other id, exact dates and times, session notes
// (free text the user wrote), body weight, target weight, measurements, calories.
public final class CoachPromptBuilder {

    static final String INSTRUCTIONS = """
            You are a strength training coach inside a workout tracking app. You are given \
            a summary of one lifter's most recent training sessions and their goal.

            Write a short review for the lifter, addressed to them as "you":
            1. What is going well, pointing at specific lifts and numbers.
            2. What to watch: stalls, big jumps in load, muscle groups trained much more \
            or much less than the others, very high effort on every set.
            3. Two or three concrete suggestions for the next session.

            Base every statement on the data given. If the data is too thin to say \
            something, say so plainly instead of guessing. Weights are in kilograms. RPE \
            is the lifter's own 1 to 10 rating of how hard a set was. Do not give medical \
            advice and do not diagnose pain or injury; suggest seeing a professional for \
            those. Keep it under 250 words, in plain text with no markdown.""";

    private CoachPromptBuilder() {
    }

    // Sessions must be finished ones, most recent first.
    public static String trainingSummary(
            List<WorkoutSessionResponse> sessions, GoalDirection goal, Instant now) {
        StringBuilder out = new StringBuilder();
        out.append("Goal: ").append(goal == null ? "not set" : describe(goal)).append('\n');
        out.append("Sessions, most recent first:\n");

        for (WorkoutSessionResponse session : sessions) {
            long daysAgo = Math.max(0, Duration.between(session.finishedAt(), now).toDays());
            out.append('\n').append(daysAgo == 0 ? "Today" : daysAgo + " days ago").append('\n');

            if (session.exerciseEntries().isEmpty()) {
                out.append("  (no exercises logged)\n");
            }
            for (ExerciseEntryResponse entry : session.exerciseEntries()) {
                out.append("  ").append(entry.exercise().name())
                        .append(" (").append(entry.exercise().muscleGroup().name().toLowerCase()).append("): ");
                List<SetEntryResponse> done = entry.sets().stream().filter(SetEntryResponse::completed).toList();
                out.append(done.isEmpty()
                        ? "no completed sets"
                        : done.stream().map(CoachPromptBuilder::describe).collect(Collectors.joining(", ")));
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String describe(GoalDirection goal) {
        return goal.name().toLowerCase().replace('_', ' ');
    }

    private static String describe(SetEntryResponse set) {
        StringBuilder text = new StringBuilder();
        if (set.weight() != null) {
            text.append(set.weight().stripTrailingZeros().toPlainString()).append(" kg x ");
        }
        text.append(set.reps() == null ? "?" : set.reps()).append(set.weight() == null ? " reps" : "");
        if (set.rpe() != null) {
            text.append(" @ RPE ").append(set.rpe().stripTrailingZeros().toPlainString());
        }
        return text.toString();
    }
}
