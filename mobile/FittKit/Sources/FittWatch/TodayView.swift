import FittCore
import SwiftUI

/// What to train today, with Start, or Resume when a workout is already going.
struct TodayView: View {
    let model: WatchModel
    @State private var showingWorkout = false

    var body: some View {
        NavigationStack {
            Group {
                if !model.hasLoadedToday {
                    TodayLoadingView(model: model)
                } else if let session = model.activeSession {
                    ResumeView(session: session, nextLift: model.nextLift, problem: model.todayProblem) {
                        showingWorkout = true
                    }
                } else if let next = model.nextLift, let day = next.day {
                    NextDayView(model: model, next: next, day: day) {
                        Task {
                            await model.startWorkout()
                            if model.activeSession != nil { showingWorkout = true }
                        }
                    }
                } else {
                    NoPlanView(problem: model.todayProblem)
                }
            }
            .navigationTitle("Today")
            .navigationDestination(isPresented: $showingWorkout) {
                if let session = model.activeSession {
                    WorkoutInProgressView(session: session)
                }
            }
        }
    }
}

/// Before the first read has finished, or after it failed with nothing to show.
private struct TodayLoadingView: View {
    let model: WatchModel

    var body: some View {
        VStack(spacing: 8) {
            if let problem = model.todayProblem, !model.isLoadingToday {
                Text(TodayText.problem(problem))
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("todayProblem")
                Button("Try Again") {
                    Task { await model.loadToday() }
                }
                .tint(WatchTheme.accent)
            } else {
                ProgressView().tint(WatchTheme.accent)
            }
        }
    }
}

/// The day the server says is next, its exercises, and Start.
private struct NextDayView: View {
    let model: WatchModel
    let next: NextLift
    let day: WorkoutPlanDay
    let start: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("NEXT UP")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(WatchTheme.textSecondary)
                    Text(day.dayLabel)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(WatchTheme.textPrimary)
                        .accessibilityIdentifier("dayLabel")
                    Text(TodayText.position(next))
                        .font(.footnote)
                        .foregroundStyle(WatchTheme.textSecondary)
                }

                if next.alreadyTrainedToday {
                    Text("You trained today already. This is the next session.")
                        .font(.footnote)
                        .foregroundStyle(WatchTheme.textSecondary)
                }

                Button(action: start) {
                    if model.isStarting {
                        ProgressView().tint(WatchTheme.onAccent)
                    } else {
                        Text("Start").font(.headline)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(WatchTheme.accent)
                .foregroundStyle(WatchTheme.onAccent)
                .disabled(model.isStarting)
                .accessibilityIdentifier("startWorkout")

                if let problem = model.todayProblem {
                    Text(TodayText.problem(problem))
                        .font(.footnote)
                        .foregroundStyle(WatchTheme.textSecondary)
                        .accessibilityIdentifier("todayProblem")
                }

                ForEach(day.orderedExercises) { item in
                    HStack {
                        Text(item.exercise.name)
                            .font(.footnote)
                            .foregroundStyle(WatchTheme.textPrimary)
                            .lineLimit(2)
                        Spacer(minLength: 4)
                        Text("\(item.sets) × \(item.reps)")
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(WatchTheme.textSecondary)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(.horizontal, 4)
        }
    }
}

/// A workout is already going, started here or on the phone.
private struct ResumeView: View {
    let session: WorkoutSession
    let nextLift: NextLift?
    let problem: WatchModel.TodayProblem?
    let resume: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("IN PROGRESS")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(WatchTheme.accent)
                Text(session.startedAt, style: .timer)
                    .font(.title2.monospacedDigit().weight(.semibold))
                    .foregroundStyle(WatchTheme.textPrimary)
                    .accessibilityLabel("Workout running")
                Text(TodayText.loggedSets(session.loggedSetCount))
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)

                Button("Resume", action: resume)
                    .buttonStyle(.borderedProminent)
                    .tint(WatchTheme.accent)
                    .foregroundStyle(WatchTheme.onAccent)
                    .accessibilityIdentifier("resumeWorkout")

                if let problem {
                    Text(TodayText.problem(problem))
                        .font(.footnote)
                        .foregroundStyle(WatchTheme.textSecondary)
                }
            }
            .padding(.horizontal, 4)
        }
    }
}

/// No active plan, or a plan with no days.
private struct NoPlanView: View {
    let problem: WatchModel.TodayProblem?

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                Image(systemName: "list.bullet.clipboard")
                    .font(.title2)
                    .foregroundStyle(WatchTheme.accent)
                    .accessibilityHidden(true)
                Text("No plan yet")
                    .font(.headline)
                    .foregroundStyle(WatchTheme.textPrimary)
                Text("Make a plan active in FITT on your iPhone and it shows here.")
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)
                    .multilineTextAlignment(.center)
                if let problem {
                    Text(TodayText.problem(problem))
                        .font(.footnote)
                        .foregroundStyle(WatchTheme.textSecondary)
                }
            }
            .padding(.horizontal, 4)
        }
    }
}

/// The workout that is going. Logging sets from the watch is the next piece;
/// for now this shows the time and what has been logged.
struct WorkoutInProgressView: View {
    let session: WorkoutSession

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(session.startedAt, style: .timer)
                    .font(.title.monospacedDigit().weight(.semibold))
                    .foregroundStyle(WatchTheme.accent)
                    .accessibilityLabel("Workout running")
                    .accessibilityIdentifier("workoutTimer")
                Text(TodayText.loggedSets(session.loggedSetCount))
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)

                ForEach(session.orderedEntries) { entry in
                    HStack {
                        Text(entry.exercise.name)
                            .font(.footnote)
                            .foregroundStyle(WatchTheme.textPrimary)
                            .lineLimit(2)
                        Spacer(minLength: 4)
                        Text("\(entry.sets.count)")
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(WatchTheme.textSecondary)
                            .accessibilityLabel(TodayText.loggedSets(entry.sets.count))
                    }
                    .accessibilityElement(children: .combine)
                }

                Text("Log sets in FITT on your iPhone for now.")
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)
            }
            .padding(.horizontal, 4)
        }
        .navigationTitle("Workout")
    }
}

/// The words on the Today screens. Kept apart from the views so a test can read them.
enum TodayText {
    static func position(_ next: NextLift) -> String {
        var parts = ["Day \(next.dayNumber) of \(next.daysInRotation)", "Week \(next.weekInCycle)"]
        if next.deload { parts.append("Deload") }
        return parts.joined(separator: " · ")
    }

    static func loggedSets(_ count: Int) -> String {
        count == 1 ? "1 set logged" : "\(count) sets logged"
    }

    static func problem(_ problem: WatchModel.TodayProblem) -> String {
        switch problem {
        case .offline: "No connection to FITT."
        case .failed: "Could not reach FITT. Try again soon."
        }
    }
}
