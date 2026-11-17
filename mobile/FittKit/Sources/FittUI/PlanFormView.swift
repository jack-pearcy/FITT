import FittCore
import SwiftUI

/// The form for a plan, shown as a sheet: empty for a new plan, or filled in
/// with a saved plan that is being changed.
struct PlanFormView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    /// The id of the plan being changed. Nil for a new plan.
    private let editingPlanId: Int64?
    @State private var draft: PlanDraft

    init(editing plan: WorkoutPlan? = nil) {
        editingPlanId = plan?.id
        _draft = State(initialValue: plan.map(PlanDraft.init(plan:)) ?? PlanDraft())
    }
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
            .navigationTitle(editingPlanId == nil ? "New Plan" : "Edit Plan")
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
            let saved = if let editingPlanId {
                await model.updatePlan(editingPlanId, with: draft)
            } else {
                await model.createPlan(draft)
            }
            if saved {
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
    @State private var creating = false
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
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("New") { creating = true }
                    // Until the library has loaded, an error on this screen means the
                    // load failed, so the form is kept away from that state.
                    .disabled(model.exercises == nil)
                    .accessibilityIdentifier("newExercise")
            }
        }
        .sheet(isPresented: $creating) {
            // A new exercise is made to be used, so it goes straight into the day.
            NewExerciseView { exercise in choose(exercise) }
        }
        .task {
            if model.exercises == nil {
                await model.loadExercises()
            }
        }
    }
}
