import FittCore
import Foundation
import Testing
@testable import FittAPI
@testable import FittUI

@Suite("Settings")
@MainActor
struct SettingsTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let metricJSON = #"{"heightCm":180.34,"weightKg":81.65,"dateOfBirth":"1990-05-17","biologicalSex":"MALE","activityLevel":"ACTIVE","unitsPreference":"METRIC","updatedAt":"2026-11-20T10:15:30Z"}"#
    private let notFound = #"{"type":"about:blank","title":"Biometrics Not Found","status":404,"detail":"biometrics not found for user 7"}"#

    private var metric: Biometrics {
        Biometrics(
            heightCm: Decimal(string: "180.34")!, weightKg: Decimal(string: "81.65")!,
            dateOfBirth: CalendarDate(year: 1990, month: 5, day: 17),
            biologicalSex: .male, activityLevel: .active, unitsPreference: .metric)
    }

    private func backend(_ server: StubServer) -> Backend {
        server.backend(tokenStore: InMemoryTokenStore(token: "stored"))
    }

    /// A signed-in user 7 whose onboarding is done and whose units are metric.
    private func signedInModel(_ server: StubServer) async -> AppModel {
        server.respond { _ in
            .json(200, #"{"biometricsComplete":true,"goalsComplete":true,"onboardingComplete":true,"unitsPreference":"METRIC"}"#)
        }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let now = now
        let model = AppModel(backend: server.backend(tokenStore: store), tokenStore: store, now: { now })
        await model.start()
        return model
    }

    private func calls(_ server: StubServer, after count: Int) -> [String] {
        server.requests.dropFirst(count).map { "\($0.method) \($0.path)" }
    }

    // MARK: Backend

    @Test("biometrics are read from the biometrics route")
    func readsBiometrics() async throws {
        let server = StubServer()
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }

        let biometrics = try await backend(server).biometrics(userId: 10)

        #expect(biometrics == metric)
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/identity/biometrics")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("a 404 on biometrics reads as none saved, with or without a problem body")
    func noBiometrics() async throws {
        let server = StubServer()
        let notFound = notFound
        server.respond { _ in .json(404, notFound) }
        #expect(try await backend(server).biometrics(userId: 10) == nil)

        server.respond { _ in StubResponse(status: 404) }
        #expect(try await backend(server).biometrics(userId: 10) == nil)
    }

    @Test("any other failure reading biometrics is still an error")
    func biometricsFailure() async {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 500) }
        await #expect(throws: BackendError.unexpectedStatus(500)) {
            try await backend(server).biometrics(userId: 10)
        }

        server.respond(nil)
        await #expect(throws: BackendError.transport) {
            try await backend(server).biometrics(userId: 10)
        }
    }

    @Test("changing units puts the preference alone to the units route and accepts a 204")
    func setsUnits() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await backend(server).setUnits(.imperial, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "PUT")
        #expect(request.path == "/api/users/10/identity/biometrics/units")
        #expect(request.headers["Authorization"] == "Bearer stored")
        #expect(String(decoding: request.body, as: UTF8.self) == #"{"unitsPreference":"IMPERIAL"}"#)
    }

    @Test("units written and read back through the backend come out the same")
    func unitsRoundTrip() async throws {
        let server = StubServer()
        let stored = Locked("METRIC")
        let metricJSON = metricJSON
        server.respond { request in
            guard request.method == "PUT" else {
                return .json(200, metricJSON.replacingOccurrences(of: "METRIC", with: stored.withLock { $0 }))
            }
            let body = try? JSONSerialization.jsonObject(with: request.body) as? [String: String]
            stored.withLock { $0 = body?["unitsPreference"] ?? "" }
            return StubResponse(status: 204)
        }
        let backend = backend(server)

        try await backend.setUnits(.imperial, userId: 10)

        #expect(try await backend.biometrics(userId: 10) == metric.with(units: .imperial))
    }

    // MARK: AppModel

    @Test("biometrics load, and the units follow what the server holds")
    func loadsBiometrics() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let imperial = metricJSON.replacingOccurrences(of: "METRIC", with: "IMPERIAL")
        server.respond { _ in .json(200, imperial) }

        await model.loadBiometrics()

        #expect(model.biometrics == metric.with(units: .imperial))
        #expect(model.units == .imperial)
        #expect(!model.hasNoBiometrics)
        #expect(model.errorMessage == nil)
    }

    @Test("a user with no biometrics saved is told apart from one still loading")
    func loadsNoBiometrics() async {
        let server = StubServer()
        let model = await signedInModel(server)
        #expect(!model.hasNoBiometrics)
        let notFound = notFound
        server.respond { _ in .json(404, notFound) }

        await model.loadBiometrics()

        #expect(model.biometrics == nil)
        #expect(model.hasNoBiometrics)
        #expect(model.errorMessage == nil)
    }

    private func screenState(_ model: AppModel) -> ScreenState<Biometrics> {
        SettingsContent.screenState(
            biometrics: model.biometrics, isMissing: model.hasNoBiometrics,
            errorMessage: model.errorMessage, isOffline: model.isOffline)
    }

    @Test("a biometrics read the server fails is an error, not a user with none saved")
    func loadFails() async {
        let server = StubServer()
        let model = await signedInModel(server)
        server.respond { _ in StubResponse(status: 500) }

        await model.loadBiometrics()

        #expect(model.biometrics == nil)
        #expect(!model.hasNoBiometrics)
        #expect(!model.isOffline)
        #expect(model.errorMessage == "The server gave an unexpected answer. Try again.")
        #expect(screenState(model) == .failed("The server gave an unexpected answer. Try again."))
    }

    @Test("a biometrics read with no connection is offline, not a user with none saved")
    func loadOffline() async {
        let server = StubServer()
        let model = await signedInModel(server)
        server.respond(nil)

        await model.loadBiometrics()

        #expect(model.biometrics == nil)
        #expect(!model.hasNoBiometrics)
        #expect(model.isOffline)
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
        #expect(screenState(model) == .offline)
    }

    @Test("trying again after a failed read reaches the biometrics and clears the error")
    func retryAfterFailure() async {
        let server = StubServer()
        let model = await signedInModel(server)
        server.respond(nil)
        await model.loadBiometrics()
        #expect(screenState(model) == .offline)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }

        await model.loadBiometrics()

        #expect(model.biometrics == metric)
        #expect(!model.hasNoBiometrics)
        #expect(!model.isOffline)
        #expect(model.errorMessage == nil)
        #expect(screenState(model) == .content(metric))
    }

    @Test("trying again after a failed read reaches a user with none saved")
    func retryReachesEmpty() async {
        let server = StubServer()
        let model = await signedInModel(server)
        server.respond { _ in StubResponse(status: 500) }
        await model.loadBiometrics()
        let notFound = notFound
        server.respond { _ in .json(404, notFound) }

        await model.loadBiometrics()

        #expect(model.errorMessage == nil)
        #expect(screenState(model) == .empty)
    }

    @Test("a refresh that fails keeps the loaded biometrics on screen")
    func refreshFails() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }
        await model.loadBiometrics()
        server.respond(nil)

        await model.loadBiometrics()

        #expect(model.biometrics == metric)
        #expect(!model.hasNoBiometrics)
        #expect(model.isOffline)
        #expect(screenState(model) == .content(metric))
    }

    @Test("a biometrics read cancelled with its screen shows no error")
    func cancelledLoadIsQuiet() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }

        // The task is cancelled before it first runs, as SwiftUI cancels the
        // task of a screen that has gone away.
        let task = Task { await model.loadBiometrics() }
        task.cancel()
        await task.value

        #expect(model.errorMessage == nil)
        #expect(!model.isOffline)
        #expect(!model.hasNoBiometrics)
        #expect(screenState(model) == .loading)
    }

    @Test("a cancelled refresh leaves the loaded biometrics with no error")
    func cancelledRefreshIsQuiet() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }
        await model.loadBiometrics()

        let task = Task { await model.loadBiometrics() }
        task.cancel()
        await task.value

        #expect(model.biometrics == metric)
        #expect(model.errorMessage == nil)
        #expect(!model.isOffline)
    }

    @Test("changing units saves through the server, and the app follows")
    func setUnitsRoundTrip() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }
        await model.loadBiometrics()
        let before = server.requests.count
        server.respond { _ in StubResponse(status: 204) }

        await model.setUnits(.imperial)

        #expect(model.units == .imperial)
        #expect(model.biometrics == metric.with(units: .imperial))
        #expect(model.errorMessage == nil)
        #expect(!model.isWorking)
        #expect(calls(server, after: before) == ["PUT /api/users/7/identity/biometrics/units"])
        #expect(String(decoding: server.requests.last?.body ?? Data(), as: UTF8.self) == #"{"unitsPreference":"IMPERIAL"}"#)
    }

    @Test("choosing the units already in use sends nothing")
    func setSameUnits() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let before = server.requests.count

        await model.setUnits(.metric)

        #expect(server.requests.count == before)
    }

    @Test("a units change the server cannot take keeps the old units and says why")
    func setUnitsOffline() async {
        let server = StubServer()
        let model = await signedInModel(server)
        server.respond(nil)

        await model.setUnits(.imperial)

        #expect(model.units == .metric)
        #expect(model.isOffline)
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
    }

    @Test("a units change the server rejects keeps the old units")
    func setUnitsRejected() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let notFound = notFound
        server.respond { _ in .json(404, notFound) }

        await model.setUnits(.imperial)

        #expect(model.units == .metric)
        #expect(model.errorMessage == "biometrics not found for user 7")
    }

    @Test("saving body measures posts them to the onboarding route and keeps what was saved")
    func updatesBiometrics() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let before = server.requests.count
        server.respond { _ in StubResponse(status: 204) }
        var draft = BiometricsDraft(biometrics: metric)
        draft.weight = "83"

        let saved = await model.updateBiometrics(draft)

        #expect(saved)
        #expect(model.biometrics?.weightKg == 83)
        #expect(model.biometrics?.heightCm == Decimal(string: "180.34"))
        #expect(!model.hasNoBiometrics)
        #expect(!model.isWorking)
        #expect(calls(server, after: before) == ["POST /api/users/7/identity/onboarding/biometrics"])
    }

    @Test("body measures that fail the checks are not sent")
    func updateBiometricsInvalid() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let before = server.requests.count
        var draft = BiometricsDraft(biometrics: metric)
        draft.weight = "5"

        let saved = await model.updateBiometrics(draft)

        #expect(!saved)
        #expect(model.fieldErrors["weightKg"] == "Enter a weight between 20 and 700 kg")
        #expect(server.requests.count == before)
    }

    @Test("a save the server cannot take leaves the saved measures as they were")
    func updateBiometricsOffline() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }
        await model.loadBiometrics()
        server.respond(nil)
        var draft = BiometricsDraft(biometrics: metric)
        draft.weight = "83"

        let saved = await model.updateBiometrics(draft)

        #expect(!saved)
        #expect(model.biometrics == metric)
        #expect(model.isOffline)
    }

    /// Answers the next request matching `method` only after the user has
    /// signed out, as a slow answer that lands after Sign Out does.
    private func signOutBeforeAnswering(
        _ server: StubServer, _ model: AppModel, method: String, with response: StubResponse
    ) {
        server.respond { request in
            guard request.method == method else { return StubResponse(status: 500) }
            let signedOut = DispatchSemaphore(value: 0)
            Task { @MainActor in
                await model.signOut()
                signedOut.signal()
            }
            signedOut.wait()
            return response
        }
    }

    @Test("biometrics that answer after sign out are dropped")
    func lateLoadAfterSignOut() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let imperial = metricJSON.replacingOccurrences(of: "METRIC", with: "IMPERIAL")
        signOutBeforeAnswering(server, model, method: "GET", with: .json(200, imperial))

        await model.loadBiometrics()

        #expect(model.phase == .signedOut)
        #expect(model.biometrics == nil)
        #expect(!model.hasNoBiometrics)
        #expect(model.units == .metric)
    }

    @Test("a missing-biometrics answer after sign out is dropped")
    func lateEmptyLoadAfterSignOut() async {
        let server = StubServer()
        let model = await signedInModel(server)
        signOutBeforeAnswering(server, model, method: "GET", with: .json(404, notFound))

        await model.loadBiometrics()

        #expect(!model.hasNoBiometrics)
        #expect(model.biometrics == nil)
    }

    @Test("a units change that answers after sign out is dropped")
    func lateUnitsAfterSignOut() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let metricJSON = metricJSON
        server.respond { _ in .json(200, metricJSON) }
        await model.loadBiometrics()
        signOutBeforeAnswering(server, model, method: "PUT", with: StubResponse(status: 204))

        await model.setUnits(.imperial)

        #expect(model.phase == .signedOut)
        #expect(model.units == .metric)
        #expect(model.biometrics == nil)
    }

    @Test("saved body measures that answer after sign out are dropped")
    func lateSaveAfterSignOut() async {
        let server = StubServer()
        let model = await signedInModel(server)
        signOutBeforeAnswering(server, model, method: "POST", with: StubResponse(status: 204))
        var draft = BiometricsDraft(biometrics: metric.with(units: .imperial))
        draft.weight = "183"

        let saved = await model.updateBiometrics(draft)

        #expect(!saved)
        #expect(model.phase == .signedOut)
        #expect(model.biometrics == nil)
        #expect(!model.hasNoBiometrics)
        #expect(model.units == .metric)
    }

    @Test("sign out forgets the biometrics and goes back to metric")
    func signOutForgets() async {
        let server = StubServer()
        let model = await signedInModel(server)
        let imperial = metricJSON.replacingOccurrences(of: "METRIC", with: "IMPERIAL")
        server.respond { _ in .json(200, imperial) }
        await model.loadBiometrics()

        await model.signOut()

        #expect(model.biometrics == nil)
        #expect(!model.hasNoBiometrics)
        #expect(model.units == .metric)
    }
}
