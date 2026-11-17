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
                        List(history) { session in
                            NavigationLink {
                                SessionDetailView(session: session, planName: planName(for: session))
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(session.startedAt.formatted(date: .abbreviated, time: .shortened))
                                    Text([planName(for: session), session.contentText, session.durationText]
                                        .compactMap { $0 }
                                        .joined(separator: " · "))
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .accessibilityIdentifier("session.\(session.id)")
                        }
                        .refreshable { await load() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await load() }
                } else {
                    ProgressView()
                }
            }
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
        List {
            Section {
                LabeledContent("Started", value: session.startedAt.formatted(date: .abbreviated, time: .shortened))
                if let durationText = session.durationText {
                    LabeledContent("Length", value: durationText)
                }
                if let planName {
                    LabeledContent("Plan", value: planName)
                }
            }

            if let notes = session.notes {
                Section("Notes") {
                    Text(notes)
                        .accessibilityIdentifier("sessionNotes")
                }
            }

            ForEach(session.orderedEntries) { entry in
                Section(entry.exercise.name) {
                    if entry.sets.isEmpty {
                        Text("No sets logged")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(entry.orderedSets) { set in
                        LabeledContent("Set \(set.setNumber)") {
                            VStack(alignment: .trailing) {
                                Text(set.summary)
                                    .monospacedDigit()
                                if let rpe = set.rpe {
                                    Text("RPE \(rpe.formatted())")
                                        .font(.footnote)
                                }
                            }
                        }
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
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("error")
                }
            }
        }
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
