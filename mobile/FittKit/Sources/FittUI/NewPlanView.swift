import FittCore
import SwiftUI

/// The form for a new plan, shown as a sheet over the Plans tab.
struct NewPlanView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PlanDraft()
    /// The day an exercise is being picked for, while the picker is open.
    @State private var pickingForDay: UUID?
    @FocusState private var focus: Field?

    private enum Field: Hashable {
        case name
        case label(UUID)
    }

    private var splitType: Binding<SplitType> {
        Binding(get: { draft.splitType }, set: { draft.setSplitType($0) })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $draft.name)
                        .focused($focus, equals: .name)
                        .submitLabel(.done)
                        .accessibilityIdentifier("planName")
                    FieldError(field: "name")

                    Picker("Split", selection: splitType) {
                        ForEach(SplitType.allCases, id: \.self) { split in
                            Text(split.label).tag(split)
                        }
                    }
                    .accessibilityIdentifier("split")

                    Stepper("Days a week: \(draft.daysPerWeek)", value: $draft.daysPerWeek, in: PlanDraft.daysPerWeek)
                        .accessibilityIdentifier("daysPerWeek")
                }

                ForEach($draft.days) { $day in
                    let index = draft.days.firstIndex { $0.id == day.id } ?? 0
                    Section {
                        TextField("Day name", text: $day.label)
                            .focused($focus, equals: .label(day.id))
                            .submitLabel(.done)
                            .accessibilityIdentifier("dayLabel.\(index)")
                        FieldError(field: "days[\(index)].dayLabel")

                        ForEach($day.slots) { $slot in
                            SlotRow(slot: $slot)
                        }
                        .onDelete { day.slots.remove(atOffsets: $0) }
                        FieldError(field: "days[\(index)].exercises")

                        Button("Add Exercise") {
                            focus = nil
                            pickingForDay = day.id
                        }
                        .disabled(day.slots.count >= PlanDraft.maxExercisesPerDay)
                        .accessibilityIdentifier("addExercise.\(index)")

                        if draft.canRemoveDay {
                            Button("Remove Day", role: .destructive) {
                                draft.removeDay(day.id)
                            }
                            .accessibilityIdentifier("removeDay.\(index)")
                        }
                    } header: {
                        Text("Day \(index + 1)")
                    }
                }

                Section {
                    Button("Add Day") { draft.addDay() }
                        .disabled(!draft.canAddDay)
                        .accessibilityIdentifier("addDay")
                    FieldError(field: "days")
                }

                ErrorSection()
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("New Plan")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("savePlan")
                }
            }
            .navigationDestination(item: $pickingForDay) { dayId in
                ExercisePickerView { exercise in
                    draft.add(exercise, toDay: dayId)
                    pickingForDay = nil
                }
            }
            // A message about the last attempt is stale once the user edits the form.
            .onChange(of: draft) { model.clearErrors() }
        }
        .onAppear { model.clearErrors() }
        .interactiveDismissDisabled(model.isWorking)
    }

    private func save() {
        focus = nil
        Task {
            if await model.createPlan(draft) {
                dismiss()
            }
        }
    }
}

/// One exercise in a day, with its sets and reps.
private struct SlotRow: View {
    @Binding var slot: PlanDraft.Slot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(slot.exercise.name)
            Stepper("Sets: \(slot.sets)", value: $slot.sets, in: PlanDraft.sets)
                .font(.subheadline)
            Stepper("Reps: \(slot.reps)", value: $slot.reps, in: PlanDraft.reps)
                .font(.subheadline)
        }
    }
}

/// The exercise library, searchable and grouped by muscle group.
private struct ExercisePickerView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    let choose: (Exercise) -> Void

    var body: some View {
        Group {
            if let exercises = model.exercises {
                let groups = ExerciseCatalog.groups(exercises, matching: query)
                if groups.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    List(groups) { group in
                        Section(group.muscleGroup.label) {
                            ForEach(group.exercises) { exercise in
                                Button(exercise.name) { choose(exercise) }
                                    .foregroundStyle(.primary)
                                    .accessibilityIdentifier("exercise.\(exercise.id)")
                            }
                        }
                    }
                }
            } else if let message = model.errorMessage {
                ContentUnavailableView {
                    Label("Could not load", systemImage: "wifi.exclamationmark")
                } description: {
                    Text(message)
                } actions: {
                    Button("Try Again") {
                        Task { await model.loadExercises() }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Add Exercise")
        .searchable(text: $query, prompt: "Search exercises")
        .task {
            if model.exercises == nil {
                await model.loadExercises()
            }
        }
    }
}
