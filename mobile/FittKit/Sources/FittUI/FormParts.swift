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
