import FittCore
import SwiftUI

extension BiologicalSex {
    var label: String {
        switch self {
        case .male: "Male"
        case .female: "Female"
        }
    }
}

extension ActivityLevel {
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

/// Step one of onboarding: the body measurements the calorie maths needs.
struct BiometricsStepView: View {
    @Environment(AppModel.self) private var model
    @State private var draft = BiometricsDraft()

    var body: some View {
        BiometricsForm(draft: $draft, submitTitle: "Continue") {
            Task { await model.saveBiometrics(draft) }
        }
        .navigationTitle("About You")
    }
}

/// The body measures form, shared by onboarding and Settings.
struct BiometricsForm: View {
    @Environment(AppModel.self) private var model
    @Binding var draft: BiometricsDraft
    /// Off when editing saved measures: the fields are filled in the saved
    /// units, and Settings has its own control for changing them.
    var showsUnits = true
    let submitTitle: String
    let action: () -> Void
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
        ThemedForm {
            if showsUnits {
                Section {
                    Picker("Units", selection: $draft.units) {
                        Text("Metric").tag(UnitsPreference.metric)
                        Text("Imperial").tag(UnitsPreference.imperial)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("units")
                }
            }

            Section {
                // The unit sits in the label: a filled field hides its placeholder.
                LabeledContent(draft.units == .metric ? "Height (cm)" : "Height (ft, in)") {
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

                LabeledContent(draft.units == .metric ? "Weight (kg)" : "Weight (lb)") {
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
                .quietValue()
                FieldError(field: "activityLevel")
            } footer: {
                Text("Used to estimate the calories you burn each day.")
            }

            ErrorSection()
            SubmitSection(title: submitTitle) {
                focus = nil
                action()
            }
        }
        .scrollDismissesKeyboard(.interactively)
        // The bar, not a keyboard toolbar item: Settings shows this form inside
        // the tab view, where a toolbar item does not appear.
        .keyboardDoneBar(isShown: focus != nil) { focus = nil }
        .canvas()
        // A message about the last attempt is stale once the user edits the form.
        .onChange(of: draft) { model.clearErrors() }
    }
}

/// Step two of onboarding: the goal and how the daily calorie number is set.
struct GoalsStepView: View {
    @Environment(AppModel.self) private var model
    @State private var draft = GoalsDraft()

    var body: some View {
        GoalsForm(draft: $draft, submitTitle: "Finish") {
            Task { await model.saveGoals(draft) }
        }
        .canvas()
        .navigationTitle("Your Goal")
    }
}
