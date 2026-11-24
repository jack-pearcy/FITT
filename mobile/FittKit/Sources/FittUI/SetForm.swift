import FittCore
import SwiftUI

/// A field on the workout screen that can hold the keyboard.
struct WorkoutField: Hashable {
    let entryId: Int64
    let name: String
}

/// The row that logs the next set of one exercise. The weight and reps arrive
/// filled in, step by plates and by single reps, and stay editable. The button
/// names the set it will log, so one tap logs what is shown.
struct SetForm: View {
    @Binding var draft: SetDraft
    let entry: ExerciseEntry
    let units: UnitsPreference
    let isWorking: Bool
    let focus: FocusState<WorkoutField?>.Binding
    let onLog: () -> Void

    var body: some View {
        let plate = PlateStep(units: units)
        let plateText = "\(plate.size.formatted()) \(units.weightName(plural: plate.size != 1))"
        VStack(spacing: Theme.Space.s) {
            StepperField(
                title: "Weight",
                placeholder: units.weightLabel,
                text: $draft.weight,
                decimal: true,
                spokenName: "Weight in \(units.weightName())",
                decreaseLabel: "Decrease weight by \(plateText)",
                increaseLabel: "Increase weight by \(plateText)",
                id: "weight",
                orderIndex: entry.orderIndex,
                focus: focus,
                field: WorkoutField(entryId: entry.id, name: "weight"),
                step: plate.stepped
            )
            StepperField(
                title: "Reps",
                placeholder: "Reps",
                text: $draft.reps,
                decimal: false,
                spokenName: "Reps",
                decreaseLabel: "Decrease reps by 1",
                increaseLabel: "Increase reps by 1",
                id: "reps",
                orderIndex: entry.orderIndex,
                focus: focus,
                field: WorkoutField(entryId: entry.id, name: "reps"),
                step: RepStep.stepped
            )
            HStack(spacing: Theme.Space.s) {
                TextField("RPE", text: $draft.rpe)
                    .numberEntry(decimal: true)
                    .focused(focus, equals: WorkoutField(entryId: entry.id, name: "rpe"))
                    .frame(minHeight: 44)
                    .accessibilityLabel("RPE, optional")
                    .accessibilityIdentifier("rpe.\(entry.orderIndex)")
                Button(action: onLog) {
                    Text(logTitle)
                        .monospacedDigit()
                        .frame(minHeight: 32)
                }
                // A style of its own also keeps the tap on the button. With the default
                // style, a button in a list row makes the whole row one tap target.
                .buttonStyle(PillButtonStyle())
                .frame(minHeight: 44)
                .disabled(isWorking)
                .accessibilityLabel(logSpoken)
                .accessibilityIdentifier("logSet.\(entry.orderIndex)")
            }
        }
    }

    private var logTitle: String {
        Self.logTitle(draft: draft, units: units)
    }

    /// The log button's text: "Log 80 kg × 8" when the fields hold a set,
    /// "Log 12 reps" or "Log 1 rep" without a weight, "Log" otherwise.
    static func logTitle(draft: SetDraft, units: UnitsPreference) -> String {
        guard let reps = Int(draft.reps.trimmingCharacters(in: .whitespaces)) else { return "Log" }
        let weight = draft.weight.trimmingCharacters(in: .whitespaces)
        let repsWord = reps == 1 ? "rep" : "reps"
        return weight.isEmpty ? "Log \(reps) \(repsWord)" : "Log \(weight) \(units.weightLabel) × \(reps)"
    }

    private var logSpoken: String {
        Self.logSpoken(draft: draft, units: units)
    }

    /// What VoiceOver reads for the log button: "Log set, 80 kilograms for 8
    /// reps", with the unit and the reps singular when the number is one.
    static func logSpoken(draft: SetDraft, units: UnitsPreference) -> String {
        guard let reps = Int(draft.reps.trimmingCharacters(in: .whitespaces)) else { return "Log set" }
        let repsWord = reps == 1 ? "rep" : "reps"
        let weight = draft.weight.trimmingCharacters(in: .whitespaces)
        let plural = DecimalInput.parse(weight) != 1
        return weight.isEmpty
            ? "Log set, \(reps) \(repsWord)"
            : "Log set, \(weight) \(units.weightName(plural: plural)) for \(reps) \(repsWord)"
    }
}

/// A number field with a minus button before it and a plus button after it.
private struct StepperField: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    let decimal: Bool
    let spokenName: String
    let decreaseLabel: String
    let increaseLabel: String
    let id: String
    let orderIndex: Int
    let focus: FocusState<WorkoutField?>.Binding
    let field: WorkoutField
    let step: (String, Bool) -> String
    @ScaledMetric private var fieldWidth: CGFloat = 72

    var body: some View {
        HStack(spacing: Theme.Space.s) {
            Text(title)
                .foregroundStyle(Theme.textSecondary)
                .accessibilityHidden(true)
            Spacer(minLength: Theme.Space.s)
            Button { text = step(text, false) } label: { Image(systemName: "minus") }
                .buttonStyle(StepButtonStyle())
                .accessibilityLabel(decreaseLabel)
                .accessibilityIdentifier("\(id)Down.\(orderIndex)")
            TextField(placeholder, text: $text)
                .numberEntry(decimal: decimal)
                .monospacedDigit()
                .focused(focus, equals: field)
                .frame(width: fieldWidth)
                .frame(minHeight: 44)
                .accessibilityLabel(spokenName)
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: text = step(text, true)
                    case .decrement: text = step(text, false)
                    @unknown default: break
                    }
                }
                .accessibilityIdentifier("\(id).\(orderIndex)")
            Button { text = step(text, true) } label: { Image(systemName: "plus") }
                .buttonStyle(StepButtonStyle())
                .accessibilityLabel(increaseLabel)
                .accessibilityIdentifier("\(id)Up.\(orderIndex)")
        }
    }
}

/// A round 44 point target on the nested surface, for a plus or a minus.
private struct StepButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric private var size: CGFloat = 44

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Theme.textPrimary)
            .frame(width: max(size, 44), height: max(size, 44))
            .background(Theme.well, in: Circle())
            .contentShape(Circle())
            .opacity(isEnabled ? (configuration.isPressed ? 0.7 : 1) : 0.4)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.92 : 1)
            .animation(Theme.animation(Theme.quick, reduceMotion: reduceMotion), value: configuration.isPressed)
    }
}

/// "Last time: 80 kg × 8, 8, 7" under an exercise's name.
struct LastTimeLine: View {
    let lastTime: LastTime
    let units: UnitsPreference

    var body: some View {
        Text("Last time: \(lastTime.text(units: units))")
            .font(.footnote)
            .monospacedDigit()
            .foregroundStyle(Theme.textSecondary)
            .textCase(nil)
            .accessibilityLabel(lastTime.accessibilityText(units: units))
    }
}

// MARK: Previews

/// One exercise as the workout screen shows it, fed by fixtures and no network.
private struct ExerciseRowPreview: View {
    let entry: ExerciseEntry
    let suggestion: ProgressionSuggestion?
    let history: [WorkoutSession]
    var units: UnitsPreference = .metric
    @State private var draft: SetDraft?
    @FocusState private var focus: WorkoutField?

    var body: some View {
        let prefill = SetPrefill.draft(
            exerciseId: entry.exercise.id, loggedToday: entry.sets,
            suggestion: suggestion, history: history, units: units)
        ThemedList {
            Section {
                ForEach(entry.orderedSets) { set in
                    LabeledContent("Set \(set.setNumber)") {
                        Text(set.summary(units: units)).monospacedDigit()
                    }
                    .setTile()
                }
                SetForm(
                    draft: Binding(get: { draft ?? prefill }, set: { draft = $0 }),
                    entry: entry, units: units, isWorking: false, focus: $focus, onLog: {})
            } header: {
                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                    Text(entry.exercise.name).sectionLabel()
                    if let last = LastTime.find(exerciseId: entry.exercise.id, in: history) {
                        LastTimeLine(lastTime: last, units: units)
                    }
                }
            } footer: {
                if let text = suggestion?.targetText(units: units) {
                    Text("Suggested: \(text)")
                }
            }
        }
        .fittTheme()
    }
}

private enum PreviewData {
    static let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)

    static func set(_ number: Int, reps: Int, kilograms: Decimal) -> SetEntry {
        SetEntry(id: Int64(number), setNumber: number, reps: reps, weight: kilograms, rpe: nil, completed: true)
    }

    static func entry(sets: [SetEntry] = []) -> ExerciseEntry {
        ExerciseEntry(id: 30, orderIndex: 0, exercise: bench, sets: sets)
    }

    static let history = [
        WorkoutSession(
            id: 11, planId: 5,
            startedAt: Date(timeIntervalSince1970: 1_790_000_000),
            finishedAt: Date(timeIntervalSince1970: 1_790_003_600),
            exerciseEntries: [entry(sets: [set(1, reps: 8, kilograms: 80), set(2, reps: 8, kilograms: 80), set(3, reps: 7, kilograms: 80)])]),
    ]

    static let suggestion = ProgressionSuggestion(
        exerciseId: 1, modelUsed: .doubleProgression, suggestedWeight: Decimal(825) / 10,
        suggestedReps: 8, suggestedSets: 3, reasoning: "All sets reached 8 reps, so the weight goes up.")
}

#Preview("No history") {
    ExerciseRowPreview(entry: PreviewData.entry(), suggestion: nil, history: [])
}

#Preview("Last time shown") {
    ExerciseRowPreview(entry: PreviewData.entry(), suggestion: nil, history: PreviewData.history)
}

#Preview("Suggestion target") {
    ExerciseRowPreview(entry: PreviewData.entry(), suggestion: PreviewData.suggestion, history: PreviewData.history)
}

#Preview("After a logged set") {
    ExerciseRowPreview(
        entry: PreviewData.entry(sets: [PreviewData.set(1, reps: 8, kilograms: Decimal(825) / 10)]),
        suggestion: PreviewData.suggestion, history: PreviewData.history)
}

#Preview("Pounds") {
    ExerciseRowPreview(entry: PreviewData.entry(), suggestion: nil, history: PreviewData.history, units: .imperial)
}
