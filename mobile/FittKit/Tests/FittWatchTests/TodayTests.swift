import FittAPI
import FittCore
import Foundation
import Testing
@testable import FittWatch

@MainActor
@Suite struct TodayTests {
    private let startedAt = Date(timeIntervalSince1970: 1_800_000_000)

    private func connectedModel(_ backend: FakeBackend, userId: Int64 = 7) -> WatchModel {
        let token = makeWatchToken(userId: userId, expiresAt: Date().addingTimeInterval(3600))
        let model = WatchModel(
            tokenStore: InMemoryTokenStore(token: token, refreshToken: "refresh"),
            backend: backend,
            now: { [startedAt] in startedAt })
        model.start()
        return model
    }

    @Test("today's day and the workout in progress are read together")
    func loadsBoth() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        backend.sessionList = [Fixtures.session(id: 1, finished: true), Fixtures.session(id: 2, finished: false)]
        let model = connectedModel(backend)

        await model.loadToday()

        #expect(model.hasLoadedToday)
        #expect(model.nextLift?.day?.dayLabel == "Pull")
        #expect(model.activeSession?.id == 2)
        #expect(model.todayProblem == nil)
        #expect(backend.calls.contains("nextLift 7"))
        #expect(backend.calls.contains("sessions 7"))
    }

    @Test("with only finished workouts there is nothing to resume")
    func nothingToResume() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        backend.sessionList = [Fixtures.session(id: 1, finished: true)]
        let model = connectedModel(backend)

        await model.loadToday()

        #expect(model.activeSession == nil)
    }

    @Test("start makes a session for the plan and adds the day's exercises in order")
    func startsTheDay() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        await model.startWorkout()

        #expect(backend.started == [NewWorkoutSession(startedAt: startedAt, planId: 3)])
        #expect(backend.addedExercises == [20, 10])
        #expect(model.activeSession?.exerciseEntries.map(\.exercise.id) == [20, 10])
        #expect(model.isStarting == false)
    }

    @Test("start does nothing while a workout is already going")
    func noSecondWorkout() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        backend.sessionList = [Fixtures.session(id: 2, finished: false)]
        let model = connectedModel(backend)
        await model.loadToday()

        await model.startWorkout()

        #expect(backend.started.isEmpty)
        #expect(model.activeSession?.id == 2)
    }

    @Test("start first asks for the open workout, and starts one when there is none")
    func startAsksFirst() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        await model.startWorkout()

        #expect(Array(backend.calls.drop { $0 != "open 7" }.prefix(2)) == ["open 7", "start 7"])
        #expect(model.activeSession?.id == 99)
    }

    @Test("a workout started on the phone after Today loaded is resumed, not started again")
    func resumesPhoneWorkout() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()
        #expect(model.activeSession == nil)

        let fromPhone = Fixtures.session(id: 44, finished: false, entries: [
            ExerciseEntry(id: 1, orderIndex: 0, exercise: Fixtures.pullUp, sets: []),
        ])
        backend.openResult = .success(fromPhone)
        await model.startWorkout()

        #expect(model.activeSession == fromPhone)
        #expect(backend.started.isEmpty)
        #expect(backend.addedExercises.isEmpty)
        #expect(model.todayProblem == nil)
        #expect(model.isStarting == false)
    }

    @Test("a 409 on start resumes the session the server names")
    func conflictResumes() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        backend.conflictOnStart = Fixtures.session(id: 45, finished: false)
        await model.startWorkout()

        #expect(model.activeSession?.id == 45)
        #expect(backend.calls.suffix(3) == ["open 7", "start 7", "session 45"])
        #expect(backend.addedExercises.isEmpty)
        #expect(model.todayProblem == nil)
    }

    @Test("a server without the open route answers it with an error, and start works as before", arguments: [404, 400])
    func olderServer(status: Int) async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        backend.openResult = .failure(.problem(status: status, APIProblem(status: status, title: "Bad Request")))
        await model.startWorkout()

        #expect(backend.started.count == 1)
        #expect(model.activeSession?.exerciseEntries.map(\.exercise.id) == [20, 10])
        #expect(model.todayProblem == nil)
    }

    @Test("no connection on the open route starts nothing and says so")
    func openOffline() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        backend.openResult = .failure(.transport)
        await model.startWorkout()

        #expect(backend.started.isEmpty)
        #expect(model.activeSession == nil)
        #expect(model.todayProblem == .offline)
    }

    @Test("start does nothing with no plan day")
    func noDay() async {
        let backend = FakeBackend()
        backend.next = NextLift(hasActivePlan: false)
        let model = connectedModel(backend)
        await model.loadToday()

        await model.startWorkout()

        #expect(backend.started.isEmpty)
        #expect(model.activeSession == nil)
    }

    @Test("an exercise that fails to add keeps the session that was made, so it is resumed, not started twice")
    func partialStart() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        backend.failAddingExercise = 10
        let model = connectedModel(backend)
        await model.loadToday()

        await model.startWorkout()

        #expect(model.activeSession?.exerciseEntries.map(\.exercise.id) == [20])
        #expect(model.todayProblem == .failed)
        await model.startWorkout()
        #expect(backend.started.count == 1)
    }

    @Test("no connection keeps what was shown and says so")
    func offlineKeepsData() async {
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = connectedModel(backend)
        await model.loadToday()

        backend.failure = BackendError.transport
        await model.loadToday()

        #expect(model.todayProblem == .offline)
        #expect(model.nextLift?.day?.dayLabel == "Pull")
        #expect(model.hasLoadedToday)
    }

    @Test("a server error is told apart from no connection")
    func serverError() async {
        let backend = FakeBackend()
        backend.failure = BackendError.unexpectedStatus(500)
        let model = connectedModel(backend)

        await model.loadToday()

        #expect(model.todayProblem == .failed)
        #expect(model.hasLoadedToday == false)
    }

    @Test("a refused session sends the watch back to connecting once its tokens are gone")
    func sessionEnded() async {
        let store = InMemoryTokenStore(
            token: makeWatchToken(userId: 7, expiresAt: Date().addingTimeInterval(3600)), refreshToken: "refresh")
        let backend = FakeBackend()
        backend.next = Fixtures.nextLift()
        let model = WatchModel(tokenStore: store, backend: backend)
        model.start()
        await model.loadToday()

        // The client clears the store when a refresh is refused, then throws.
        store.clear()
        backend.failure = BackendError.unauthorized
        await model.loadToday()

        #expect(model.phase == .notConnected)
        #expect(model.nextLift == nil)
        #expect(model.hasLoadedToday == false)
    }

    @Test("a watch that is not connected reads nothing")
    func notConnectedReadsNothing() async {
        let backend = FakeBackend()
        let model = WatchModel(tokenStore: InMemoryTokenStore(), backend: backend)
        model.start()

        await model.loadToday()

        #expect(backend.calls.isEmpty)
    }

    @Test("the day line names the place in the rotation, the week and a deload")
    func positionWords() {
        #expect(TodayText.position(Fixtures.nextLift()) == "Day 2 of 3 · Week 1")
        #expect(TodayText.position(Fixtures.nextLift(deload: true)) == "Day 2 of 3 · Week 1 · Deload")
        #expect(TodayText.loggedSets(1) == "1 set logged")
        #expect(TodayText.loggedSets(4) == "4 sets logged")
    }
}

enum Fixtures {
    static let deadlift = Exercise(id: 10, name: "Deadlift", muscleGroup: .back, custom: false)
    static let pullUp = Exercise(id: 20, name: "Pull Up", muscleGroup: .back, custom: false)

    static func exercise(_ id: Int64) -> Exercise {
        id == 10 ? deadlift : pullUp
    }

    /// Pull Up is stored second but ordered first, so the tests catch a start
    /// that ignores the plan's order.
    static func nextLift(deload: Bool = false) -> NextLift {
        NextLift(
            hasActivePlan: true, planId: 3, planName: "Push Pull Legs", daysInRotation: 3, dayNumber: 2,
            weekInCycle: 1, deload: deload,
            day: WorkoutPlanDay(id: 5, orderIndex: 1, dayLabel: "Pull", exercises: [
                PlanDayExercise(id: 51, orderIndex: 1, sets: 3, reps: 8, exercise: deadlift),
                PlanDayExercise(id: 52, orderIndex: 0, sets: 3, reps: 8, exercise: pullUp),
            ]))
    }

    static func session(id: Int64, finished: Bool, entries: [ExerciseEntry] = []) -> WorkoutSession {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        return WorkoutSession(
            id: id, planId: 3, startedAt: start, finishedAt: finished ? start.addingTimeInterval(3600) : nil,
            exerciseEntries: entries)
    }
}

/// Stands in for the server.
final class FakeBackend: WatchBackend, @unchecked Sendable {
    private let lock = NSLock()
    private let redeemHandler: @Sendable (String) async throws -> SessionTokens
    private var _next = NextLift(hasActivePlan: false)
    private var _sessions: [WorkoutSession] = []
    private var _failure: (any Error)?
    private var _failAdding: Int64?
    private var _calls: [String] = []
    private var _started: [NewWorkoutSession] = []
    private var _added: [Int64] = []
    private var _session: WorkoutSession?

    init(redeem: @escaping @Sendable (String) async throws -> SessionTokens = { _ in throw BackendError.transport }) {
        redeemHandler = redeem
    }

    var next: NextLift { get { lock.withLock { _next } } set { lock.withLock { _next = newValue } } }
    var sessionList: [WorkoutSession] { get { lock.withLock { _sessions } } set { lock.withLock { _sessions = newValue } } }
    var failure: (any Error)? { get { lock.withLock { _failure } } set { lock.withLock { _failure = newValue } } }
    var failAddingExercise: Int64? { get { lock.withLock { _failAdding } } set { lock.withLock { _failAdding = newValue } } }
    var calls: [String] { lock.withLock { _calls } }
    var started: [NewWorkoutSession] { lock.withLock { _started } }
    var addedExercises: [Int64] { lock.withLock { _added } }

    private func record(_ call: String) throws {
        let failure = lock.withLock {
            _calls.append(call)
            return _failure
        }
        if let failure { throw failure }
    }

    func redeemDeviceLink(code: String) async throws -> SessionTokens {
        try await redeemHandler(code)
    }

    func nextLift(userId: Int64) async throws -> NextLift {
        try record("nextLift \(userId)")
        return next
    }

    func sessions(userId: Int64) async throws -> [WorkoutSession] {
        try record("sessions \(userId)")
        return sessionList
    }

    /// What the open-session route answers: nil is a 204.
    var openResult: Result<WorkoutSession?, BackendError> {
        get { lock.withLock { _openResult } }
        set { lock.withLock { _openResult = newValue } }
    }
    private var _openResult: Result<WorkoutSession?, BackendError> = .success(nil)
    /// When set, start answers 409 naming this open session.
    var conflictOnStart: WorkoutSession? {
        get { lock.withLock { _conflict } }
        set { lock.withLock { _conflict = newValue } }
    }
    private var _conflict: WorkoutSession?

    func openSession(userId: Int64) async throws -> WorkoutSession? {
        try record("open \(userId)")
        return try openResult.get()
    }

    func session(_ sessionId: Int64, userId: Int64) async throws -> WorkoutSession {
        try record("session \(sessionId)")
        guard let named = conflictOnStart, named.id == sessionId else { throw BackendError.unexpectedStatus(404) }
        return named
    }

    func startSession(_ session: NewWorkoutSession, userId: Int64) async throws -> WorkoutSession {
        try record("start \(userId)")
        if let named = conflictOnStart { throw BackendError.openSessionExists(openSessionId: named.id) }
        let made = Fixtures.session(id: 99, finished: false)
        lock.withLock {
            _started.append(session)
            _session = made
        }
        return made
    }

    func addEntry(exerciseId: Int64, sessionId: Int64, userId: Int64) async throws -> WorkoutSession {
        try record("add \(exerciseId)")
        return try lock.withLock {
            if _failAdding == exerciseId { throw BackendError.unexpectedStatus(500) }
            _added.append(exerciseId)
            let current = _session ?? Fixtures.session(id: sessionId, finished: false)
            let entry = ExerciseEntry(
                id: Int64(current.exerciseEntries.count + 1), orderIndex: current.exerciseEntries.count,
                exercise: Fixtures.exercise(exerciseId), sets: [])
            let updated = WorkoutSession(
                id: current.id, planId: current.planId, startedAt: current.startedAt, finishedAt: nil,
                exerciseEntries: current.exerciseEntries + [entry])
            _session = updated
            return updated
        }
    }
}

/// An unsigned token with the two claims the app reads.
func makeWatchToken(userId: Int64, expiresAt: Date) -> String {
    func segment(_ text: String) -> String {
        Data(text.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    let payload = "{\"uid\":\(userId),\"exp\":\(Int(expiresAt.timeIntervalSince1970))}"
    return [segment("{\"alg\":\"HS256\"}"), segment(payload), segment("signature")].joined(separator: ".")
}
