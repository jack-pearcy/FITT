import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("Screen state")
struct ScreenStateTests {
    private let session = WorkoutSession(
        id: 1,
        planId: nil,
        startedAt: Date(timeIntervalSince1970: 1_800_000_000),
        finishedAt: Date(timeIntervalSince1970: 1_800_003_600),
        exerciseEntries: []
    )

    private func state(history: [WorkoutSession]?, error: String? = nil, offline: Bool = false) -> ScreenState<Int> {
        ScreenState(history: history, errorMessage: error, isOffline: offline) { sessions in
            sessions.isEmpty ? nil : sessions.count
        }
    }

    @Test("nothing loaded and no error is loading")
    func loading() {
        #expect(state(history: nil) == .loading)
    }

    @Test("a loaded history with something to show is content")
    func content() {
        #expect(state(history: [session]) == .content(1))
    }

    @Test("a loaded history with nothing to show is empty")
    func empty() {
        #expect(state(history: []) == .empty)
    }

    @Test("a load that failed is an error with its message")
    func failed() {
        #expect(state(history: nil, error: "The server gave an unexpected answer. Try again.")
            == .failed("The server gave an unexpected answer. Try again."))
    }

    @Test("a load that could not reach the server is offline")
    func offline() {
        #expect(state(history: nil, error: "Cannot reach the server. Check the connection.", offline: true) == .offline)
    }

    @Test("a loaded history stays on screen when a later refresh fails")
    func keepsContentOnError() {
        #expect(state(history: [session], error: "Cannot reach the server. Check the connection.", offline: true) == .content(1))
    }
}
