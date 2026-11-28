import FittCore
import SwiftUI

/// Settings, reached from Account: the units every weight is shown in, and the
/// body measures saved at onboarding.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var isEditing = false

    var body: some View {
        SettingsContent(
            state: SettingsContent.screenState(
                biometrics: model.biometrics,
                isMissing: model.hasNoBiometrics,
                errorMessage: model.errorMessage,
                isOffline: model.isOffline
            ),
            units: model.units,
            isWorking: model.isWorking,
            errorMessage: model.errorMessage,
            setUnits: { chosen in Task { await model.setUnits(chosen) } },
            edit: { isEditing = true },
            retry: { await model.loadBiometrics() }
        )
        .navigationTitle("Settings")
        .navigationDestination(isPresented: $isEditing) {
            EditBiometricsView(biometrics: model.biometrics)
        }
        .task { await model.loadBiometrics() }
        // A message about a failed change must not follow the user back to
        // Account. Only while signed in: when the session has ended, the
        // message is for the sign-in screen.
        .onDisappear {
            if case .signedIn = model.phase, !isEditing { model.clearErrors() }
        }
    }
}

struct SettingsContent: View {
    let state: ScreenState<Biometrics>
    let units: UnitsPreference
    let isWorking: Bool
    /// What went wrong with the last change, shown under the units.
    let errorMessage: String?
    let setUnits: (UnitsPreference) -> Void
    let edit: () -> Void
    let retry: () async -> Void

    /// Loaded measures stay on screen when a later change fails. Empty is a
    /// user the server holds no measures for.
    nonisolated static func screenState(
        biometrics: Biometrics?,
        isMissing: Bool,
        errorMessage: String?,
        isOffline: Bool
    ) -> ScreenState<Biometrics> {
        if let biometrics {
            .content(biometrics)
        } else if isMissing {
            .empty
        } else if let errorMessage {
            isOffline ? .offline : .failed(errorMessage)
        } else {
            .loading
        }
    }

    var body: some View {
        StatesView(
            state: state,
            empty: EmptyState(
                title: "No body measures yet",
                systemImage: "ruler",
                message: "Add your height and weight to choose your units and get a calorie estimate.",
                actionTitle: "Add Body Measures",
                action: edit
            ),
            retry: retry
        ) { biometrics in
            ThemedList {
                Section {
                    // A menu row, so the whole row is the target.
                    Picker("Units", selection: Binding(get: { units }, set: { setUnits($0) })) {
                        Text("Metric").tag(UnitsPreference.metric)
                        Text("Imperial").tag(UnitsPreference.imperial)
                    }
                    .disabled(isWorking)
                    .accessibilityIdentifier("units")
                    .quietValue()
                } header: {
                    Text("Units").sectionLabel()
                } footer: {
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        Text(units == .metric
                            ? "Weights are shown in kilograms and heights in centimeters."
                            : "Weights are shown in pounds and heights in feet and inches.")
                        if let errorMessage {
                            Text(errorMessage)
                                .foregroundStyle(Theme.negative)
                                .accessibilityIdentifier("error")
                        }
                    }
                }

                Section {
                    LabeledContent("Height", value: units.heightText(centimeters: biometrics.heightCm))
                        .accessibilityIdentifier("height")
                    LabeledContent("Weight", value: units.bodyWeightText(kilograms: biometrics.weightKg))
                        .accessibilityIdentifier("weight")
                    LabeledContent("Date of birth", value: Self.dayText(biometrics.dateOfBirth))
                    LabeledContent("Sex", value: biometrics.biologicalSex.label)
                    LabeledContent("Activity", value: biometrics.activityLevel.label)
                    Button("Edit Body Measures", action: edit)
                        .disabled(isWorking)
                        .accessibilityIdentifier("editBodyMeasures")
                } header: {
                    Text("Body measures").sectionLabel()
                } footer: {
                    Text("Used to estimate the calories you burn each day.")
                }
            }
            // The measures fade to their values in the new unit.
            .motion(value: units)
            .motion(value: biometrics)
        }
        .canvas()
    }

    /// "May 17, 1990" in the user's language.
    private static func dayText(_ day: CalendarDate) -> String {
        day.date()?.formatted(date: .long, time: .omitted) ?? day.iso
    }
}

/// Changing body measures after onboarding, in the form onboarding uses.
private struct EditBiometricsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: BiometricsDraft
    private let isFirstSave: Bool

    init(biometrics: Biometrics?) {
        _draft = State(initialValue: biometrics.map(BiometricsDraft.init(biometrics:)) ?? BiometricsDraft())
        isFirstSave = biometrics == nil
    }

    var body: some View {
        BiometricsForm(draft: $draft, showsUnits: isFirstSave, submitTitle: "Save") {
            Task {
                if await model.updateBiometrics(draft) {
                    dismiss()
                }
            }
        }
        .navigationTitle("Body Measures")
        .onAppear { model.clearErrors() }
    }
}

#if DEBUG
/// Local data for previews. Nothing here reaches the network.
private enum SettingsFixtures {
    static let biometrics = Biometrics(
        heightCm: 180, weightKg: 82, dateOfBirth: CalendarDate(year: 1990, month: 5, day: 17),
        biologicalSex: .male, activityLevel: .active, unitsPreference: .metric)
}

@MainActor
private func settingsPreview(
    _ state: ScreenState<Biometrics>, units: UnitsPreference = .metric, errorMessage: String? = nil
) -> some View {
    NavigationStack {
        SettingsContent(
            state: state, units: units, isWorking: false, errorMessage: errorMessage,
            setUnits: { _ in }, edit: {}, retry: {})
            .navigationTitle("Settings")
    }
    .fittTheme()
}

#Preview("Settings, metric") { settingsPreview(.content(SettingsFixtures.biometrics)) }
#Preview("Settings, imperial") {
    settingsPreview(.content(SettingsFixtures.biometrics.with(units: .imperial)), units: .imperial)
}
#Preview("Settings, change failed") {
    settingsPreview(.content(SettingsFixtures.biometrics), errorMessage: "Cannot reach the server. Check the connection.")
}
#Preview("Settings, loading") { settingsPreview(.loading) }
#Preview("Settings, empty") { settingsPreview(.empty) }
#Preview("Settings, error") { settingsPreview(.failed("The server gave an unexpected answer. Try again.")) }
#Preview("Settings, offline") { settingsPreview(.offline) }
#endif
