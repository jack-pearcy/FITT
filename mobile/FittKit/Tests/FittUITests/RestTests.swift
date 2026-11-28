import FittCore
import Foundation
import Testing
@testable import FittAPI
@testable import FittUI

/// Records what the model asks of the notification centre.
@MainActor
final class FakeRestAlerts: RestAlerts {
    var allowed = true
    private(set) var permissionAsks = 0
    /// The end date of the alert pending now, if any.
    private(set) var pending: Date?
    private(set) var scheduledCount = 0
    private(set) var cancelCount = 0

    func requestPermission() async -> Bool {
        permissionAsks += 1
        return allowed
    }

    func schedule(at date: Date, now: Date) {
        pending = date
        scheduledCount += 1
    }

    func cancel() {
        pending = nil
        cancelCount += 1
    }
}

@Suite("Rest timer")
@MainActor
struct RestTests {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)
    private let bench = #"{"id":1,"name":"Barbell Bench Press","muscleGroup":"CHEST","custom":false}"#

    private func session(sets: String = "", finished: Bool = false) -> String {
        let finishedAt = finished ? #""2026-11-16T06:00:00Z""# : "null"
        let entry = #"{"id":30,"orderIndex":0,"exercise":\#(bench),"sets":[\#(sets)]}"#
        return #"{"id":12,"ownerUserId":7,"planId":5,"startedAt":"2026-11-16T05:20:32Z","finishedAt":\#(finishedAt),"notes":null,"exerciseEntries":[\#(entry)]}"#
    }

    private func setJSON(id: Int, number: Int) -> String {
        #"{"id":\#(id),"setNumber":\#(number),"reps":8,"weight":100.00,"rpe":null,"completed":true}"#
    }

    private func defaults() -> UserDefaults {
        let name = "rest-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    /// A signed-in user 7 with workout 12 open, and a clock the test moves.
    private func workoutModel(
        _ server: StubServer,
        alerts: FakeRestAlerts?,
        clock: Locked<Date>,
        settings: DeviceSettings? = nil
    ) async -> AppModel {
        server.respond { _ in
            .json(200, #"{"biometricsComplete":true,"goalsComplete":true,"onboardingComplete":true}"#)
        }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: start.addingTimeInterval(3600)))
        let model = AppModel(
            backend: server.backend(tokenStore: store), tokenStore: store,
            restAlerts: alerts, deviceSettings: settings ?? DeviceSettings(defaults: defaults()),
            now: { clock.withLock { $0 } })
        await model.start()
        let open = session()
        server.respond { _ in .json(200, "[\(open)]") }
        await model.loadActiveSession()
        return model
    }

    /// Logs one set and waits for the alert to be scheduled.
    private func logSet(_ model: AppModel, _ server: StubServer, sets: String) async -> Bool {
        let updated = session(sets: sets)
        server.respond { _ in .json(200, updated) }
        let saved = await model.logSet(SetDraft(weight: "100", reps: "8"), entryId: 30)
        await model.restAlertTask?.value
        return saved
    }

    @Test("a logged set starts rest at the default length, with an alert at its end")
    func startsAfterSet() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let clock = Locked(start)
        let model = await workoutModel(server, alerts: alerts, clock: clock)
        #expect(model.restTimer == nil)

        #expect(await logSet(model, server, sets: setJSON(id: 101, number: 1)))

        #expect(model.restTimer?.endsAt == start.addingTimeInterval(120))
        #expect(alerts.permissionAsks == 1)
        #expect(alerts.pending == start.addingTimeInterval(120))
    }

    @Test("a set that was not saved starts no rest")
    func failedSetStartsNone() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        server.respond { _ in StubResponse(status: 500) }

        #expect(!(await model.logSet(SetDraft(weight: "100", reps: "8"), entryId: 30)))

        #expect(model.restTimer == nil)
        #expect(alerts.scheduledCount == 0)
    }

    @Test("+30 s moves the timer and replaces the alert")
    func addTime() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let clock = Locked(start)
        let model = await workoutModel(server, alerts: alerts, clock: clock)
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        clock.withLock { $0 = start.addingTimeInterval(20) }

        model.addRestTime()
        await model.restAlertTask?.value

        #expect(model.restTimer?.endsAt == start.addingTimeInterval(150))
        #expect(alerts.pending == start.addingTimeInterval(150))
        #expect(alerts.scheduledCount == 2)
    }

    @Test("Skip ends rest and removes the alert")
    func skip() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))

        model.skipRest()

        #expect(model.restTimer == nil)
        #expect(alerts.pending == nil)
        #expect(alerts.cancelCount == 1)
    }

    @Test("the next set starts a new rest in place of the one running")
    func nextSetReplaces() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let clock = Locked(start)
        let model = await workoutModel(server, alerts: alerts, clock: clock)
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        clock.withLock { $0 = start.addingTimeInterval(50) }

        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1) + "," + setJSON(id: 102, number: 2))

        #expect(model.restTimer?.endsAt == start.addingTimeInterval(170))
        #expect(alerts.pending == start.addingTimeInterval(170))
    }

    @Test("finishing the workout ends rest and removes the alert")
    func finishEnds() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        let finished = session(sets: setJSON(id: 101, number: 1), finished: true)
        server.respond { request in
            request.path.hasSuffix("/finish") ? .json(200, finished) : StubResponse(status: 500)
        }

        await model.finishWorkout()

        #expect(model.activeSession == nil)
        #expect(model.restTimer == nil)
        #expect(alerts.pending == nil)
    }

    @Test("discarding the workout ends rest and removes the alert")
    func discardEnds() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        server.respond { _ in StubResponse(status: 204) }

        await model.discardWorkout()

        #expect(model.restTimer == nil)
        #expect(alerts.pending == nil)
    }

    @Test("signing out ends rest and removes the alert")
    func signOutEnds() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        server.respond { _ in StubResponse(status: 204) }

        await model.signOut()

        #expect(model.restTimer == nil)
        #expect(alerts.pending == nil)
    }

    @Test("with alerts turned down, the timer still runs and nothing is scheduled")
    func permissionDenied() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        alerts.allowed = false
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))

        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))

        #expect(model.restTimer?.endsAt == start.addingTimeInterval(120))
        #expect(alerts.permissionAsks == 1)
        #expect(alerts.scheduledCount == 0)
    }

    @Test("a skip while the permission question is open schedules nothing")
    func skipBeforeAnswer() async {
        let server = StubServer()
        let alerts = FakeRestAlerts()
        let model = await workoutModel(server, alerts: alerts, clock: Locked(start))
        let updated = session(sets: setJSON(id: 101, number: 1))
        server.respond { _ in .json(200, updated) }
        _ = await model.logSet(SetDraft(weight: "100", reps: "8"), entryId: 30)
        let task = model.restAlertTask

        // The skip lands before the scheduling task has run.
        model.skipRest()
        await task?.value

        #expect(model.restTimer == nil)
        #expect(alerts.scheduledCount == 0)
    }

    @Test("the rest length comes from Settings and is kept on the device")
    func lengthFromSettings() async {
        let server = StubServer()
        let settings = DeviceSettings(defaults: defaults())
        settings.restLength = 90
        let model = await workoutModel(server, alerts: nil, clock: Locked(start), settings: settings)
        #expect(model.restLength == 90)

        model.setRestLength(1_000)
        #expect(model.restLength == 600)
        #expect(settings.restLength == 600)

        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))
        #expect(model.restTimer?.endsAt == start.addingTimeInterval(600))
    }

    @Test("with nothing chosen, rest is two minutes")
    func defaultLength() {
        #expect(DeviceSettings(defaults: defaults()).restLength == 120)
    }

    @Test("a new length leaves the rest already running as it is")
    func lengthChangeKeepsRunning() async {
        let server = StubServer()
        let model = await workoutModel(server, alerts: nil, clock: Locked(start))
        _ = await logSet(model, server, sets: setJSON(id: 101, number: 1))

        model.setRestLength(60)

        #expect(model.restTimer?.endsAt == start.addingTimeInterval(120))
    }
}
