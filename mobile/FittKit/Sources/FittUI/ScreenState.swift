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
