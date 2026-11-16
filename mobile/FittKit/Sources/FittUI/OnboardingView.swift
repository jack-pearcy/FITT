import FittCore
import SwiftUI

private extension View {
    /// A number pad. The keyboard modifier exists only on iOS, and this module
    /// also builds on macOS for tests.
    func numberEntry(decimal: Bool) -> some View {
        #if os(iOS)
        self.keyboardType(decimal ? .decimalPad : .numberPad)
            .multilineTextAlignment(.trailing)
        #else
        self.multilineTextAlignment(.trailing)
        #endif
    }
}

/// The number pads have no return key, so without this the keyboard stays up
/// and covers the button at the bottom of the form.
private struct KeyboardDoneButton: ToolbarContent {
    let action: () -> Void

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .keyboard) {
            Spacer()
            Button("Done", action: action)
                .accessibilityIdentifier("keyboardDone")
        }
    }
}

private extension BiologicalSex {
    var label: String {
        switch self {
        case .male: "Male"
        case .female: "Female"
        }
    }
}

private extension ActivityLevel {
    var label: String {
        switch self {
        case .sedentary: "Sedentary"
        case .lightlyActive: "Lightly active"
        case .moderatelyActive: "Moderately active"
        case .active: "Active"
        case .veryActive: "Very active"
        }
    }
}

private extension GoalDirection {
    var label: String {
        switch self {
        case .bulk: "Bulk"
        case .cut: "Cut"
        case .maintain: "Maintain"
        }
    }
}

private struct SubmitSection: View {
    @Environment(AppModel.self) private var model
    let title: String
    let action: () -> Void

    var body: some View {
        Section {
            Button(action: action) {
                HStack {
                    Text(title)
                    if model.isWorking {
                        Spacer()
                        ProgressView()
                    }
                }
            }
            .disabled(model.isWorking)
            .accessibilityIdentifier("submit")
        }
    }
}

/// Step one of onboarding: the body measurements the calorie maths needs.
struct BiometricsStepView: View {
    @Environment(AppModel.self) private var model
    @State private var draft = BiometricsDraft()
    @FocusState private var focus: Field?

    private enum Field { case height, feet, inches, weight }

    /// The date picker works in `Date`; the draft keeps a plain calendar day.
    private var dateOfBirth: Binding<Date> {
        Binding(
            get: { draft.dateOfBirth.date() ?? .now },
            set: { draft.dateOfBirth = CalendarDate($0) }
        )
    }

    var body: some View {
        Form {
            Section {
                Picker("Units", selection: $draft.units) {
                    Text("Metric").tag(UnitsPreference.metric)
                    Text("Imperial").tag(UnitsPreference.imperial)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("units")
            }

            Section {
                LabeledContent("Height") {
                    if draft.units == .metric {
                        TextField("cm", text: $draft.height)
                            .numberEntry(decimal: true)
                            .focused($focus, equals: .height)
                            .accessibilityIdentifier("height")
                    } else {
                        HStack {
                            TextField("ft", text: $draft.feet)
                                .numberEntry(decimal: false)
                                .focused($focus, equals: .feet)
                                .accessibilityIdentifier("feet")
                            TextField("in", text: $draft.inches)
                                .numberEntry(decimal: true)
                                .focused($focus, equals: .inches)
                                .accessibilityIdentifier("inches")
                        }
                    }
                }
                FieldError(field: "heightCm")

                LabeledContent("Weight") {
                    TextField(draft.units == .metric ? "kg" : "lb", text: $draft.weight)
                        .numberEntry(decimal: true)
                        .focused($focus, equals: .weight)
                        .accessibilityIdentifier("weight")
                }
                FieldError(field: "weightKg")

                DatePicker("Date of birth", selection: dateOfBirth, in: ...Date.now, displayedComponents: .date)
                    .environment(\.calendar, CalendarDate.gregorian)
                    .accessibilityIdentifier("dateOfBirth")
                FieldError(field: "dateOfBirth")
            }

            Section {
                Picker("Sex", selection: $draft.sex) {
                    ForEach(BiologicalSex.allCases, id: \.self) { sex in
                        Text(sex.label).tag(Optional(sex))
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("sex")
                FieldError(field: "biologicalSex")

                Picker("Activity", selection: $draft.activity) {
                    Text("Select").tag(nil as ActivityLevel?)
                    ForEach(ActivityLevel.allCases, id: \.self) { level in
                        Text(level.label).tag(Optional(level))
                    }
                }
                .accessibilityIdentifier("activity")
                FieldError(field: "activityLevel")
            } footer: {
                Text("Used to estimate the calories you burn each day.")
            }

            ErrorSection()
            SubmitSection(title: "Continue") {
                focus = nil
                Task { await model.saveBiometrics(draft) }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("About You")
        .toolbar { KeyboardDoneButton { focus = nil } }
        // A message about the last attempt is stale once the user edits the form.
        .onChange(of: draft) { model.clearErrors() }
    }
}

/// Step two of onboarding: the goal and how the daily calorie number is set.
struct GoalsStepView: View {
    @Environment(AppModel.self) private var model
    @State private var draft = GoalsDraft()
    @FocusState private var focus: Field?

    private enum Field { case targetWeight, timelineWeeks, calories }

    var body: some View {
        Form {
            Section {
                Picker("Goal", selection: $draft.direction) {
                    ForEach(GoalDirection.allCases, id: \.self) { direction in
                        Text(direction.label).tag(Optional(direction))
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("direction")
                FieldError(field: "goalDirection")
            }

            Section {
                LabeledContent("Target weight") {
                    TextField(model.units == .metric ? "kg" : "lb", text: $draft.targetWeight)
                        .numberEntry(decimal: true)
                        .focused($focus, equals: .targetWeight)
                        .accessibilityIdentifier("targetWeight")
                }
                FieldError(field: "targetWeightKg")

                LabeledContent("Timeline") {
                    TextField("weeks", text: $draft.timelineWeeks)
                        .numberEntry(decimal: false)
                        .focused($focus, equals: .timelineWeeks)
                        .accessibilityIdentifier("timelineWeeks")
                }
                FieldError(field: "timelineWeeks")
            } header: {
                Text("Optional")
            }

            Section {
                Picker("Calories", selection: $draft.mode) {
                    Text("Automatic").tag(CalorieGoalMode.auto)
                    Text("Manual").tag(CalorieGoalMode.manual)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("calorieMode")

                if draft.mode == .manual {
                    LabeledContent("Daily calories") {
                        TextField("kcal", text: $draft.calories)
                            .numberEntry(decimal: false)
                            .focused($focus, equals: .calories)
                            .accessibilityIdentifier("calories")
                    }
                    FieldError(field: "calorieGoal")
                }
            } header: {
                Text("Daily calorie goal")
            } footer: {
                if let maintenance = model.maintenanceCalories {
                    Text("Your maintenance is about \(maintenance) calories a day. Automatic sets the goal from that and your goal above.")
                        .accessibilityIdentifier("maintenance")
                }
            }

            ErrorSection()
            SubmitSection(title: "Finish") {
                focus = nil
                Task { await model.saveGoals(draft) }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Your Goal")
        .toolbar { KeyboardDoneButton { focus = nil } }
        .onChange(of: draft) { model.clearErrors() }
        .task { await model.loadMaintenanceCalories() }
    }
}
