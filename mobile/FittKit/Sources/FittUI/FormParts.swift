import FittCore
import SwiftUI

extension MuscleGroup {
    var label: String {
        switch self {
        case .chest: "Chest"
        case .back: "Back"
        case .shoulders: "Shoulders"
        case .arms: "Arms"
        case .legs: "Legs"
        case .glutes: "Glutes"
        case .core: "Core"
        }
    }
}

extension SplitType {
    var label: String {
        switch self {
        case .pushPullLegs: "Push, pull, legs"
        case .upperLower: "Upper and lower"
        case .fullBody: "Full body"
        case .custom: "Custom"
        }
    }
}

/// A red footnote under a field, shown when the model holds a message for it.
extension ProgressionModel {
    var label: String {
        switch self {
        case .doubleProgression: "Double progression"
        case .rpeAutoregulation: "RPE autoregulation"
        case .linearPeriodization: "Linear periodization"
        }
    }

    /// One line on how the server uses the model, shown under the picker.
    var summary: String {
        switch self {
        case .doubleProgression: "Add reps at the same weight up to 12, then add weight and start again at 8."
        case .rpeAutoregulation: "Weight goes up or down to keep your logged RPE between 7 and 8.5."
        case .linearPeriodization: "Weight goes up each rotation of your plan, with a lighter fourth one."
        }
    }
}

struct FieldError: View {
    @Environment(AppModel.self) private var model
    let field: String

    var body: some View {
        if let message = model.fieldErrors[field] {
            Text(message)
                .font(.footnote)
                .foregroundStyle(.red)
                .accessibilityIdentifier("error.\(field)")
        }
    }
}

/// The message about the last attempt, when it is not tied to one field.
struct ErrorSection: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if let message = model.errorMessage {
            Section {
                Text(message)
                    .foregroundStyle(.red)
                    .accessibilityIdentifier("error")
            }
        }
    }
}

extension View {
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
struct KeyboardDoneButton: ToolbarContent {
    let action: () -> Void

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .keyboard) {
            Spacer()
            Button("Done", action: action)
                .accessibilityIdentifier("keyboardDone")
        }
    }
}

extension GoalDirection {
    var label: String {
        switch self {
        case .bulk: "Bulk"
        case .cut: "Cut"
        case .maintain: "Maintain"
        }
    }
}

struct SubmitSection: View {
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

extension View {
    /// A Done button pinned above the keyboard while `isShown` is true. The number
    /// pads have no return key, and a keyboard toolbar item does not appear on
    /// screens inside the tab view, so those screens use this bar instead.
    func keyboardDoneBar(isShown: Bool, action: @escaping () -> Void) -> some View {
        safeAreaInset(edge: .bottom) {
            if isShown {
                HStack {
                    Spacer()
                    Button("Done", action: action)
                        .accessibilityIdentifier("keyboardDone")
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
                .background(.bar)
            }
        }
    }
}
