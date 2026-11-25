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

    // MARK: Exercise screen

    private let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    private let pullUp = Exercise(id: 2, name: "Pull Up", muscleGroup: .back, custom: false)

    private func workout(_ exercise: Exercise, weight: Decimal?, finished: Bool = true) -> WorkoutSession {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        return WorkoutSession(id: 7, planId: nil, startedAt: start, finishedAt: finished ? start.addingTimeInterval(3600) : nil, exerciseEntries: [
            ExerciseEntry(id: 70, orderIndex: 0, exercise: exercise, sets: [
                SetEntry(id: 700, setNumber: 1, reps: 10, weight: weight, rpe: nil, completed: true),
            ]),
        ])
    }

    private func exerciseState(_ exercise: Exercise, history: [WorkoutSession]) -> ScreenState<ExerciseStats> {
        ExerciseStatsContent.screenState(exercise: exercise, history: history, errorMessage: nil, isOffline: false)
    }

    @Test("an exercise missing from every finished session is empty")
    func exerciseNeverTrained() {
        #expect(exerciseState(bench, history: [workout(pullUp, weight: nil)]) == .empty)
    }

    @Test("an exercise with only bodyweight sets is content with no estimate")
    func exerciseBodyweightOnly() {
        guard case .content(let stats) = exerciseState(pullUp, history: [workout(pullUp, weight: nil)]) else {
            Issue.record("expected content")
            return
        }
        #expect(stats.sessionCount == 1)
        #expect(stats.bestEstimate == nil)
    }

    @Test("an exercise only in unfinished sessions is empty")
    func exerciseOnlyUnfinished() {
        #expect(exerciseState(bench, history: [workout(bench, weight: 80, finished: false)]) == .empty)
    }
}
