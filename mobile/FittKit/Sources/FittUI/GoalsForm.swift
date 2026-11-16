import FittCore
import SwiftUI

/// The goals form, shared by onboarding and by the change goals screen.
/// The caller owns the draft and decides what submitting does.
struct GoalsForm: View {
    @Environment(AppModel.self) private var model
    @Binding var draft: GoalsDraft
    let submitTitle: String
    let submit: () -> Void
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
            } footer: {
                Text(model.units == .metric ? "Target weight is in kilograms." : "Target weight is in pounds.")
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
            SubmitSection(title: submitTitle) {
                focus = nil
                submit()
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .keyboardDoneBar(isShown: focus != nil) { focus = nil }
        // A message about the last attempt is stale once the user edits the form.
        .onChange(of: draft) { model.clearErrors() }
        .task { await model.loadMaintenanceCalories() }
    }
}
