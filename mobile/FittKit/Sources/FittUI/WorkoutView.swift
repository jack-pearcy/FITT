import FittCore
import SwiftUI

extension SetEntry {
    /// "100 kg × 8" with a weight, "8 reps" without one.
    func summary(units: UnitsPreference) -> String {
        let repsText = reps.map(String.init) ?? "0"
        guard let weight else { return "\(repsText) reps" }
        return "\(units.shown(kilograms: weight).formatted()) \(units.weightLabel) × \(repsText)"
    }
}

extension ProgressionSuggestion {
    /// "102.5 kg × 8, 3 sets". Nil when the server had no weight to suggest.
    func targetText(units: UnitsPreference) -> String? {
        guard hasTarget else { return nil }
        var text = "\(units.shown(kilograms: suggestedWeight).formatted()) \(units.weightLabel)"
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
    @State private var addingExercise = false
    /// What is typed in the notes field. Saved when the field loses focus and
    /// again before the workout is finished.
    @State private var notes = ""
    /// The exercise waiting for a yes before it is removed with its sets.
    @State private var removing: ExerciseEntry?
    /// The set that was just saved. Its tile is lit for a moment, then settles.
    @State private var justLogged: Int64?
    /// Goes up by one for each saved set, which is what the haptic listens to.
    @State private var loggedCount = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focus: WorkoutField?

    /// Entry ids from the server are positive, so zero cannot clash with one.
    private static let notesField = WorkoutField(entryId: 0, name: "notes")

    var body: some View {
        if let session = model.activeSession {
            ThemedList {
                Section {
                    WorkoutProgressCard(session: session, plannedSets: plannedSets(in: session))
                        .bareRow()
                }

                ForEach(session.orderedEntries) { entry in
                    Section {
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
                            .overlay {
                                RoundedRectangle(cornerRadius: Theme.rowRadius, style: .continuous)
                                    .strokeBorder(Theme.accent, lineWidth: 1.5)
                                    .opacity(justLogged == set.id ? 1 : 0)
                            }
                            .scaleEffect(justLogged == set.id && !reduceMotion ? 1.03 : 1)
                            .swipeActions {
                                Button("Delete", role: .destructive) {
                                    Task { await model.deleteSet(set.id, entryId: entry.id) }
                                }
                            }
                        }
                        setForm(for: entry)
                    } header: {
                        VStack(alignment: .leading, spacing: Theme.Space.xs) {
                            HStack {
                                Text(entry.exercise.name).sectionLabel()
                                Spacer()
                                Menu {
                                    Button("Remove Exercise", role: .destructive) { remove(entry) }
                                } label: {
                                    Image(systemName: "ellipsis.circle")
                                }
                                .disabled(model.isWorking)
                                .accessibilityLabel("Options for \(entry.exercise.name)")
                                .accessibilityIdentifier("entryMenu.\(entry.id)")
                            }
                            if let last = LastTime.find(exerciseId: entry.exercise.id, in: model.history ?? []) {
                                LastTimeLine(lastTime: last, units: model.units)
                                    .accessibilityIdentifier("lastTime.\(entry.exercise.id)")
                            }
                        }
                    } footer: {
                        VStack(alignment: .leading, spacing: 4) {
                            if let target = target(for: entry) {
                                Text("Plan: \(target.sets) × \(target.reps)")
                            }
                            if let suggestion = model.suggestions[entry.exercise.id] {
                                if let targetText = suggestion.targetText(units: model.units) {
                                    Text("Suggested: \(targetText)")
                                        .accessibilityIdentifier("suggestion.\(entry.exercise.id)")
                                }
                                // The server writes the reason with weights in kilograms, so it
                                // is left out for a user who works in pounds and has a target.
                                if model.units == .metric || !suggestion.hasTarget {
                                    Text(suggestion.reasoning)
                                }
                            }
                        }
                    }
                }

                if session.exerciseEntries.isEmpty {
                    Section {
                        Text("This workout has no exercises.")
                            .foregroundStyle(Theme.textSecondary)
                    }
                }

                Section {
                    Button("Add Exercise") {
                        focus = nil
                        addingExercise = true
                    }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("addExercise")
                } footer: {
                    Text("Added for this workout only. Your plan stays as it is.")
                }

                LabeledSection("Notes") {
                    TextField("How it went, what to change next time", text: $notes, axis: .vertical)
                        .lineLimit(2...6)
                        .focused($focus, equals: Self.notesField)
                        .accessibilityIdentifier("notes")
                    FieldError(field: "notes")
                }

                ErrorSection()

                Section {
                    Button {
                        focus = nil
                        Task {
                            // Notes that cannot be saved keep the workout open, so
                            // they are not lost with the screen.
                            if await model.saveNotes(notes) {
                                await model.finishWorkout()
                            }
                        }
                    } label: {
                        HStack(spacing: Theme.Space.s) {
                            Text("Finish Workout")
                            if model.isWorking {
                                ProgressView().tint(Theme.canvas)
                            }
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("finishWorkout")
                    .bareRow()
                } footer: {
                    Text("Finishing moves your plan on to its next day.")
                }

                Section {
                    Button("Discard Workout", role: .destructive) {
                        confirmingDiscard = true
                    }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("discardWorkout")
                }
            }
            // Sets and exercises slide in and out as the session changes, and a
            // suggestion fades in when it arrives.
            .motion(value: session)
            .motion(value: model.suggestions)
            .motion(Theme.quick, value: justLogged)
            .sensoryFeedback(.success, trigger: loggedCount)
            .scrollDismissesKeyboard(.interactively)
            .keyboardDoneBar(isShown: focus != nil) { focus = nil }
            // A message about the last attempt is stale once the user edits a set.
            .onChange(of: drafts) { model.clearErrors() }
            .onChange(of: notes) { model.clearErrors() }
            .onChange(of: focus) { old, new in
                if old == Self.notesField, new != Self.notesField {
                    Task { _ = await model.saveNotes(notes) }
                }
            }
            // A workout picked up after a relaunch brings its notes with it.
            .task(id: session.id) { notes = session.notes ?? "" }
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
                    : session.loggedSetCount == 1
                        ? "The set you logged will be deleted."
                        : "The \(session.loggedSetCount) sets you logged will be deleted.")
            }
            .confirmationDialog(
                "Remove \(removing?.exercise.name ?? "exercise")?",
                isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                titleVisibility: .visible,
                presenting: removing
            ) { entry in
                Button("Remove Exercise", role: .destructive) {
                    Task { await model.removeExercise(entryId: entry.id) }
                }
            } message: { entry in
                Text("The \(entry.sets.count) \(entry.sets.count == 1 ? "set" : "sets") you logged for it will be deleted.")
            }
            .sheet(isPresented: $addingExercise) {
                NavigationStack {
                    ExercisePickerView { exercise in
                        addingExercise = false
                        Task { await model.addExercise(exercise) }
                    }
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { addingExercise = false }
                                .secondaryAction()
                        }
                    }
                }
            }
            // Runs again when an exercise is added, which happens one by one as a
            // workout starts and again each time the user adds one.
            .task(id: session.exerciseEntries.map(\.id)) { await model.loadSuggestions() }
            // Last time and the fill from history come from the finished
            // workouts. Nothing waits on them.
            .task { await model.loadHistoryForWorkout() }
        }
    }

    /// The row that logs the next set of one exercise. Until the user edits
    /// it, the row shows the prefill, which follows the suggestion and the
    /// history as they arrive.
    @ViewBuilder
    private func setForm(for entry: ExerciseEntry) -> some View {
        let draft = Binding(
            get: { drafts[entry.id] ?? prefill(for: entry) },
            set: { drafts[entry.id] = $0 }
        )
        SetForm(draft: draft, entry: entry, units: model.units, isWorking: model.isWorking, focus: $focus) {
            Task {
                // The values stay in the row after a save: the next set is
                // usually the same weight and reps.
                if await model.logSet(draft.wrappedValue, entryId: entry.id) {
                    celebrate(entryId: entry.id)
                }
            }
        }
        ForEach(["weight", "reps", "rpe"], id: \.self) { field in
            FieldError(field: AppModel.setFieldKey(entryId: entry.id, field: field))
        }
    }

    private func prefill(for entry: ExerciseEntry) -> SetDraft {
        SetPrefill.draft(
            exerciseId: entry.exercise.id,
            loggedToday: entry.sets,
            suggestion: model.suggestions[entry.exercise.id],
            history: model.history ?? [],
            units: model.units)
    }

    /// Marks the newest set of an entry as just logged, and lets go of it a
    /// moment later so the tile settles back.
    private func celebrate(entryId: Int64) {
        guard let newest = model.activeSession?.exerciseEntries
            .first(where: { $0.id == entryId })?.sets.map(\.id).max()
        else { return }
        loggedCount += 1
        justLogged = newest
        Task {
            try? await Task.sleep(for: .milliseconds(650))
            if justLogged == newest { justLogged = nil }
        }
    }

    /// The sets the plan asks for across the exercises of this workout. Zero
    /// when none of them is in the plan's day.
    private func plannedSets(in session: WorkoutSession) -> Int {
        session.exerciseEntries.reduce(0) { $0 + (target(for: $1)?.sets ?? 0) }
    }

    /// An exercise with nothing logged goes at once. One with sets asks first,
    /// because the sets go with it.
    private func remove(_ entry: ExerciseEntry) {
        focus = nil
        if entry.sets.isEmpty {
            Task { await model.removeExercise(entryId: entry.id) }
        } else {
            removing = entry
        }
    }

    /// The sets and reps the plan prescribes for this exercise, when the
    /// session came from the day the server still reports as next.
    private func target(for entry: ExerciseEntry) -> PlanDayExercise? {
        model.nextLift?.day?.exercises.first { $0.exercise.id == entry.exercise.id }
    }
}

/// The top of a workout: how long it has run and how many sets are in.
private struct WorkoutProgressCard: View {
    let session: WorkoutSession
    /// Zero when the plan has no target for this workout.
    let plannedSets: Int

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.m) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                    Text("Sets").sectionLabel()
                    HStack(alignment: .firstTextBaseline, spacing: Theme.Space.xs) {
                        Text("\(session.loggedSetCount)")
                            .heroMetric()
                            .ticking()
                        if plannedSets > 0 {
                            Text("of \(plannedSets)")
                                .font(.title3.weight(.medium))
                                .foregroundStyle(Theme.textSecondary)
                                .ticking()
                        }
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: Theme.Space.xs) {
                    Text("Time").sectionLabel()
                    Text(session.startedAt, style: .timer)
                        .font(.title3.weight(.medium))
                        .metric()
                        .foregroundStyle(Theme.textPrimary)
                }
            }
            if plannedSets > 0 {
                ProgressBar(fraction: Double(session.loggedSetCount) / Double(plannedSets))
            }
        }
        .card()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(plannedSets > 0
            ? "\(session.loggedSetCount) of \(plannedSets) sets logged"
            : "\(session.loggedSetCount) \(session.loggedSetCount == 1 ? "set" : "sets") logged")
        .accessibilityIdentifier("workoutProgress")
    }
}
