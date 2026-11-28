import FittCore
import SwiftUI

private extension WorkoutSession {
    /// "45 min" or "1 hr, 5 min". Nil for a session with no usable length.
    var durationText: String? {
        guard let duration else { return nil }
        return Duration.seconds(duration.rounded())
            .formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
    }

    /// "3 exercises, 9 sets"
    var contentText: String {
        let exercises = exerciseEntries.count
        return "\(exercises) \(exercises == 1 ? "exercise" : "exercises"), \(loggedSetCount) \(loggedSetCount == 1 ? "set" : "sets")"
    }
}

/// Finished workouts, newest first.
struct HistoryView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            Group {
                if let history = model.history {
                    if history.isEmpty {
                        ContentUnavailableView(
                            "No workouts yet",
                            systemImage: "clock.arrow.circlepath",
                            description: Text("A workout shows up here once you finish it.")
                        )
                    } else {
                        List {
                            Section {
                                NavigationLink {
                                    MuscleVolumeView()
                                } label: {
                                    Label("Weekly volume by muscle", systemImage: "chart.bar")
                                        .foregroundStyle(Theme.textPrimary)
                                        .frame(minHeight: 44)
                                }
                                .accessibilityHint("Sets for each muscle group, week by week")
                                .accessibilityIdentifier("muscleVolume")
                                .listRowBackground(Theme.card)
                            }
                            RecordsSection(records: ExerciseStats.records(history, limit: 3), units: model.units)
                            ForEach(history) { session in
                                NavigationLink {
                                    SessionDetailView(session: session, planName: planName(for: session))
                                } label: {
                                    HStack(spacing: Theme.Space.m) {
                                        VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                            Text(session.startedAt.formatted(date: .abbreviated, time: .shortened))
                                                .font(.headline)
                                                .foregroundStyle(Theme.textPrimary)
                                            Text([planName(for: session), session.contentText]
                                                .compactMap { $0 }
                                                .joined(separator: " · "))
                                                .font(.footnote)
                                                .foregroundStyle(Theme.textSecondary)
                                        }
                                        Spacer(minLength: 0)
                                        if let durationText = session.durationText {
                                            Text(durationText)
                                                .font(.footnote.weight(.medium))
                                                .metric()
                                                .foregroundStyle(Theme.textSecondary)
                                                .padding(.horizontal, Theme.Space.s)
                                                .padding(.vertical, 3)
                                                .background(Theme.well, in: Capsule())
                                        }
                                    }
                                    .padding(.vertical, Theme.Space.xs)
                                }
                                .accessibilityIdentifier("session.\(session.id)")
                                .listRowBackground(Theme.card)
                            }
                        }
                        .themedScroll()
                        // A finished workout slides in at the top, and a deleted one slides out.
                        .motion(value: history)
                        .refreshable { await load() }
                    }
                } else if model.isOffline {
                    OfflineView { await load() }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await load() }
                } else {
                    SkeletonList()
                }
            }
            .canvas()
            .navigationTitle("History")
        }
        .task { await load() }
    }

    /// Plans are read too, for their names. A session only stores the plan's id.
    private func load() async {
        await model.loadSessions()
        if model.plans == nil {
            await model.loadPlans()
        }
    }

    /// Nil for a session with no plan, a plan that was deleted, or plans not loaded.
    private func planName(for session: WorkoutSession) -> String? {
        guard let planId = session.planId else { return nil }
        return model.plans?.first { $0.id == planId }?.name
    }
}

/// One finished workout: when it was, and every set of every exercise.
private struct SessionDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var confirmingDelete = false

    let session: WorkoutSession
    let planName: String?

    var body: some View {
        ThemedList {
            Section {
                VStack(alignment: .leading, spacing: Theme.Space.m) {
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        Text(planName ?? "Workout").sectionLabel()
                        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.xs) {
                            Text("\(session.loggedSetCount)").heroMetric()
                            Text(session.loggedSetCount == 1 ? "set" : "sets")
                                .font(.title3.weight(.medium))
                                .foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Text([session.startedAt.formatted(date: .abbreviated, time: .shortened), session.durationText]
                        .compactMap { $0 }
                        .joined(separator: " · "))
                        .font(.footnote)
                        .foregroundStyle(Theme.textSecondary)
                }
                .card()
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("sessionSummary")
                .bareRow()
            }

            if let notes = session.notes {
                LabeledSection("Notes") {
                    Text(notes)
                        .accessibilityIdentifier("sessionNotes")
                }
            }

            ForEach(session.orderedEntries) { entry in
                LabeledSection(entry.exercise.name) {
                    NavigationLink {
                        ExerciseStatsView(exercise: entry.exercise)
                    } label: {
                        Label("Records and progress", systemImage: "chart.line.uptrend.xyaxis")
                    }
                    .accessibilityLabel("\(entry.exercise.name), records and progress")
                    .accessibilityIdentifier("exerciseStats.\(entry.exercise.id)")
                    if entry.sets.isEmpty {
                        Text("No sets logged")
                            .foregroundStyle(Theme.textSecondary)
                    }
                    ForEach(entry.orderedSets) { set in
                        LabeledContent("Set \(set.setNumber)") {
                            VStack(alignment: .trailing) {
                                Text(set.summary(units: model.units))
                                    .monospacedDigit()
                                if let rpe = set.rpe {
                                    Text("RPE \(rpe.formatted())")
                                        .font(.footnote)
                                }
                            }
                        }
                        .setTile()
                    }
                }
            }

            Section {
                Button("Delete Workout", role: .destructive) { confirmingDelete = true }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("deleteWorkout")
            } footer: {
                if let message = model.errorMessage {
                    Text(message)
                        .foregroundStyle(Theme.negative)
                        .accessibilityIdentifier("error")
                }
            }
        }
        .canvas()
        .navigationTitle(session.startedAt.formatted(date: .abbreviated, time: .omitted))
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .confirmationDialog("Delete this workout?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete Workout", role: .destructive) {
                Task {
                    if await model.deleteFromHistory(session.id) {
                        dismiss()
                    }
                }
            }
        } message: {
            Text("Its sets are deleted with it. If it counted toward your plan, the plan goes back a day.")
        }
        .onAppear { model.clearErrors() }
    }
}
