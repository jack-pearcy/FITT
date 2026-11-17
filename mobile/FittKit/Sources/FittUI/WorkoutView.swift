import FittCore
import SwiftUI

extension SetEntry {
    /// "100 × 8" with a weight, "8 reps" without one.
    var summary: String {
        let repsText = reps.map(String.init) ?? "0"
        guard let weight else { return "\(repsText) reps" }
        return "\(weight.formatted()) × \(repsText)"
    }
}

extension ProgressionSuggestion {
    /// "102.5 × 8, 3 sets". Nil when the server had no weight to suggest.
    var targetText: String? {
        guard hasTarget else { return nil }
        var text = suggestedWeight.formatted()
        if let suggestedReps {
            text += " × \(suggestedReps)"
        }
        if let suggestedSets {
            text += ", \(suggestedSets) \(suggestedSets == 1 ? "set" : "sets")"
        }
        return text
    }
}

/// The workout in progress: each exercise with its logged sets and a row to log the next one.
struct WorkoutView: View {
    @Environment(AppModel.self) private var model
    /// What is typed for the next set of each entry, keyed by entry id.
    @State private var drafts: [Int64: SetDraft] = [:]
    @State private var confirmingDiscard = false
    @FocusState private var focus: Field?

    private struct Field: Hashable {
        let entryId: Int64
        let name: String
    }

    var body: some View {
        if let session = model.activeSession {
            List {
                ForEach(session.orderedEntries) { entry in
                    Section {
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
                            .swipeActions {
                                Button("Delete", role: .destructive) {
                                    Task { await model.deleteSet(set.id, entryId: entry.id) }
                                }
                            }
                        }
                        setForm(for: entry)
                    } header: {
                        Text(entry.exercise.name)
                    } footer: {
                        VStack(alignment: .leading, spacing: 4) {
                            if let target = target(for: entry) {
                                Text("Plan: \(target.sets) × \(target.reps)")
                            }
                            if let suggestion = model.suggestions[entry.exercise.id] {
                                if let targetText = suggestion.targetText {
                                    Text("Suggested: \(targetText)")
                                        .accessibilityIdentifier("suggestion.\(entry.exercise.id)")
                                }
                                Text(suggestion.reasoning)
                            }
                        }
                    }
                }

                if session.exerciseEntries.isEmpty {
                    Section {
                        Text("This workout has no exercises.")
                            .foregroundStyle(.secondary)
                    }
                }

                ErrorSection()

                Section {
                    Button {
                        focus = nil
                        Task { await model.finishWorkout() }
                    } label: {
                        HStack {
                            Text("Finish Workout")
                            if model.isWorking {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("finishWorkout")

                    Button("Discard Workout", role: .destructive) {
                        confirmingDiscard = true
                    }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("discardWorkout")
                } footer: {
                    Text("Finishing moves your plan on to its next day.")
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .keyboardDoneBar(isShown: focus != nil) { focus = nil }
            // A message about the last attempt is stale once the user edits a set.
            .onChange(of: drafts) { model.clearErrors() }
            .confirmationDialog(
                "Discard this workout?",
                isPresented: $confirmingDiscard,
                titleVisibility: .visible
            ) {
                Button("Discard Workout", role: .destructive) {
                    Task { await model.discardWorkout() }
                }
            } message: {
                Text(session.loggedSetCount == 0
                    ? "Nothing has been logged yet."
                    : "The \(session.loggedSetCount) sets you logged will be deleted.")
            }
            // Runs again when an exercise is added, which happens one by one as a
            // workout starts.
            .task(id: session.exerciseEntries.map(\.id)) { await model.loadSuggestions() }
        }
    }

    /// The row that logs the next set of one exercise.
    @ViewBuilder
    private func setForm(for entry: ExerciseEntry) -> some View {
        let draft = Binding(
            get: { drafts[entry.id] ?? SetDraft() },
            set: { drafts[entry.id] = $0 }
        )
        HStack {
            TextField("Weight", text: draft.weight)
                .numberEntry(decimal: true)
                .focused($focus, equals: Field(entryId: entry.id, name: "weight"))
                .accessibilityIdentifier("weight.\(entry.orderIndex)")
            TextField("Reps", text: draft.reps)
                .numberEntry(decimal: false)
                .focused($focus, equals: Field(entryId: entry.id, name: "reps"))
                .accessibilityIdentifier("reps.\(entry.orderIndex)")
            TextField("RPE", text: draft.rpe)
                .numberEntry(decimal: true)
                .focused($focus, equals: Field(entryId: entry.id, name: "rpe"))
                .accessibilityIdentifier("rpe.\(entry.orderIndex)")
            Button("Log") {
                Task {
                    // The values stay in the row after a save: the next set is
                    // usually the same weight and reps.
                    _ = await model.logSet(draft.wrappedValue, entryId: entry.id)
                }
            }
            // Without this, a button in a list row makes the whole row one tap target.
            .buttonStyle(.borderless)
            .accessibilityIdentifier("logSet.\(entry.orderIndex)")
        }
        ForEach(["weight", "reps", "rpe"], id: \.self) { field in
            FieldError(field: AppModel.setFieldKey(entryId: entry.id, field: field))
        }
    }

    /// The sets and reps the plan prescribes for this exercise, when the
    /// session came from the day the server still reports as next.
    private func target(for entry: ExerciseEntry) -> PlanDayExercise? {
        model.nextLift?.day?.exercises.first { $0.exercise.id == entry.exercise.id }
    }
}
