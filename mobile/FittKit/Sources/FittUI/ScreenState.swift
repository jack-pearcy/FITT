import FittCore
import SwiftUI

/// The five states of a screen worked out from the loaded history.
enum ScreenState<Value> {
    case loading
    case content(Value)
    case empty
    case failed(String)
    case offline

    /// `make` returns nil when the history holds nothing for this screen.
    init(history: [WorkoutSession]?, errorMessage: String?, isOffline: Bool, make: ([WorkoutSession]) -> Value?) {
        if let history {
            self = make(history).map(ScreenState.content) ?? .empty
        } else if let errorMessage {
            self = isOffline ? .offline : .failed(errorMessage)
        } else {
            self = .loading
        }
    }
}

extension ScreenState: Equatable where Value: Equatable {}

/// What an empty screen says, and the one thing it offers to do next.
struct EmptyState {
    let title: String
    let systemImage: String
    let message: String
    let actionTitle: String
    let action: () -> Void
}

/// Shows the loading, empty, error and offline states, and `content` for the rest.
struct StatesView<Value, Content: View>: View {
    let state: ScreenState<Value>
    let empty: EmptyState
    let retry: () async -> Void
    @ViewBuilder let content: (Value) -> Content

    var body: some View {
        switch state {
        case .loading:
            SkeletonList()
        case .content(let value):
            content(value)
        case .empty:
            ContentUnavailableView {
                Label(empty.title, systemImage: empty.systemImage)
            } description: {
                Text(empty.message)
            } actions: {
                Button(empty.actionTitle, action: empty.action)
                    .accessibilityIdentifier("emptyAction")
            }
        case .failed(let message):
            LoadFailedView(message: message, retry: retry)
        case .offline:
            OfflineView(retry: retry)
        }
    }
}

/// A full-screen message for a screen with nothing loaded and no connection.
struct OfflineView: View {
    let retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label("You are offline", systemImage: "wifi.slash")
        } description: {
            Text("Connect to the internet, then try again.")
        } actions: {
            Button("Try Again") {
                Task { await retry() }
            }
            .accessibilityIdentifier("retry")
        }
    }
}

#if DEBUG
/// Local data for previews. Nothing here reaches the network.
enum StatsFixtures {
    static let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    static let pullUp = Exercise(id: 2, name: "Pull Up", muscleGroup: .back, custom: false)

    /// Six weekly sessions: bench press rising two kilograms a week, and pull ups.
    static let sessions: [WorkoutSession] = (0..<6).map { week in
        let start = Date.now.addingTimeInterval(Double(week - 5) * 7 * 86_400)
        let id = Int64(week + 1)
        return WorkoutSession(id: id, planId: nil, startedAt: start, finishedAt: start.addingTimeInterval(3600), exerciseEntries: [
            ExerciseEntry(id: id * 10, orderIndex: 0, exercise: bench, sets: [
                SetEntry(id: id * 100, setNumber: 1, reps: 8 - week % 2, weight: Decimal(80 + 2 * week), rpe: nil, completed: true),
                SetEntry(id: id * 100 + 1, setNumber: 2, reps: 6, weight: Decimal(80 + 2 * week), rpe: nil, completed: true),
            ]),
            ExerciseEntry(id: id * 10 + 1, orderIndex: 1, exercise: pullUp, sets: [
                SetEntry(id: id * 100 + 2, setNumber: 1, reps: 10, weight: nil, rpe: nil, completed: true),
            ]),
        ])
    }
}
#endif
