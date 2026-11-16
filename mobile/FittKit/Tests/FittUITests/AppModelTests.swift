import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("AppModel")
@MainActor
struct AppModelTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let onboardingDone = #"{"biometricsComplete":true,"goalsComplete":true,"onboardingComplete":true}"#

    private func model(_ server: StubServer, store: InMemoryTokenStore = InMemoryTokenStore()) -> AppModel {
        let now = now
        return AppModel(backend: server.backend(tokenStore: store), tokenStore: store, now: { now })
    }

    @Test("a successful sign in stores the token and moves to signed in")
    func signInSucceeds() async {
        let server = StubServer()
        let token = makeToken(userId: 10, expiresAt: now.addingTimeInterval(3600))
        let onboardingDone = onboardingDone
        server.respond { request in
            request.path.hasSuffix("/login") ? .json(200, #"{"token":"\#(token)"}"#) : .json(200, onboardingDone)
        }
        let store = InMemoryTokenStore()
        let model = model(server, store: store)

        await model.signIn(email: "lifter@example.com", password: "a-long-test-password")

        #expect(model.phase == .signedIn(userId: 10))
        #expect(model.onboardingComplete == true)
        #expect(store.read()?.accessToken == token)
        #expect(model.errorMessage == nil)
        #expect(!model.isWorking)
        #expect(server.requests.map(\.path) == ["/api/identity/auth/login", "/api/users/10/identity/onboarding/status"])
    }

    @Test("wrong credentials show a message and store nothing")
    func signInRejected() async {
        let server = StubServer()
        server.respond { _ in .json(401, #"{"status":401}"#) }
        let store = InMemoryTokenStore()
        let model = model(server, store: store)
        await model.start()

        await model.signIn(email: "lifter@example.com", password: "wrong")

        #expect(model.phase == .signedOut)
        #expect(model.errorMessage == "Email or password is incorrect.")
        #expect(store.read() == nil)
    }

    @Test("a token that cannot be stored is reported as a device problem")
    func signInCannotStoreToken() async {
        struct FailingStore: TokenStore {
            func read() throws -> SessionTokens? { nil }
            func save(_ tokens: SessionTokens) throws { throw KeychainError(status: -34018) }
            func clear() throws {}
        }
        let server = StubServer()
        let token = makeToken(userId: 10, expiresAt: now.addingTimeInterval(3600))
        server.respond { _ in .json(200, #"{"token":"\#(token)"}"#) }
        let store = FailingStore()
        let now = now
        let model = AppModel(backend: server.backend(tokenStore: store), tokenStore: store, now: { now })
        await model.start()

        await model.signIn(email: "lifter@example.com", password: "a-long-test-password")

        #expect(model.phase == .signedOut)
        #expect(model.errorMessage == "Signed in, but this device could not save the session.")
    }

    @Test("register checks the password on the device before calling the server")
    func registerChecksPasswordFirst() async {
        let server = StubServer()
        let model = model(server)

        await model.register(email: "lifter@example.com", password: "short")

        #expect(model.fieldErrors["password"] != nil)
        #expect(server.requests.isEmpty)
    }

    @Test("a validation failure from the server lands on the field")
    func registerShowsFieldErrors() async {
        let server = StubServer()
        server.respond { _ in
            .json(400, #"{"status":400,"detail":"Validation failed","properties":{"errors":{"email":"Must be a valid email address"}}}"#)
        }
        let model = model(server)

        await model.register(email: "not-an-email", password: "a-long-test-password")

        #expect(model.fieldErrors == ["email": "Must be a valid email address"])
        #expect(model.errorMessage == nil)
    }

    @Test("a taken email shows the server's detail")
    func registerConflict() async {
        let server = StubServer()
        server.respond { _ in .json(409, #"{"status":409,"title":"Conflict","detail":"Email already registered"}"#) }
        let model = model(server)

        await model.register(email: "lifter@example.com", password: "a-long-test-password")

        #expect(model.errorMessage == "Email already registered")
    }

    @Test("the rate limit message names the wait")
    func rateLimited() async {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 429, headers: ["Retry-After": "42"]) }
        let model = model(server)

        await model.signIn(email: "lifter@example.com", password: "a-long-test-password")

        #expect(model.errorMessage == "Too many attempts. Try again in 42 seconds.")
    }

    @Test("launch with a valid stored token restores the session")
    func startRestoresSession() async {
        let server = StubServer()
        let onboardingDone = onboardingDone
        server.respond { _ in .json(200, onboardingDone) }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let model = model(server, store: store)

        await model.start()

        #expect(model.phase == .signedIn(userId: 7))
        #expect(model.onboardingComplete == true)
    }

    @Test("launch with an expired token signs out without calling the server")
    func startDropsExpiredToken() async {
        let server = StubServer()
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(-1)))
        let model = model(server, store: store)

        await model.start()

        #expect(model.phase == .signedOut)
        #expect(store.read() == nil)
        #expect(server.requests.isEmpty)
    }

    @Test("launch with no token goes to signed out")
    func startWithoutToken() async {
        let model = model(StubServer())

        await model.start()

        #expect(model.phase == .signedOut)
    }

    @Test("a token the server rejects ends the session")
    func rejectedTokenSignsOut() async {
        let server = StubServer()
        server.respond { _ in .json(401, #"{"status":401}"#) }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let model = model(server, store: store)

        await model.start()

        #expect(model.phase == .signedOut)
        #expect(store.read() == nil)
        #expect(model.errorMessage == "Your session ended. Sign in again.")
    }

    @Test("launch while offline keeps the session and reports the connection")
    func startOffline() async {
        let server = StubServer()
        server.respond(nil)
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let model = model(server, store: store)

        await model.start()

        #expect(model.phase == .signedIn(userId: 7))
        #expect(model.onboardingComplete == nil)
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
        #expect(store.read() != nil)
    }

    @Test("sign out clears the token")
    func signOutClearsToken() async {
        let store = InMemoryTokenStore(token: "anything")
        let model = model(StubServer(), store: store)

        await model.signOut()

        #expect(model.phase == .signedOut)
        #expect(store.read() == nil)
    }

    // MARK: Onboarding

    private func status(biometrics: Bool, goals: Bool) -> String {
        #"{"biometricsComplete":\#(biometrics),"goalsComplete":\#(goals),"onboardingComplete":\#(biometrics && goals)}"#
    }

    /// A signed-in model whose server reports the given steps as done until told otherwise.
    private func signedInModel(_ server: StubServer, biometrics: Bool, goals: Bool) async -> AppModel {
        let initial = status(biometrics: biometrics, goals: goals)
        server.respond { _ in .json(200, initial) }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let model = model(server, store: store)
        await model.start()
        return model
    }

    private func validBiometrics() -> BiometricsDraft {
        var draft = BiometricsDraft()
        draft.units = .imperial
        draft.feet = "5"
        draft.inches = "10"
        draft.weight = "180"
        draft.sex = .male
        draft.activity = .active
        return draft
    }

    @Test("saving biometrics posts them, then reads the status back")
    func saveBiometricsAdvances() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: false, goals: false)
        let after = status(biometrics: true, goals: false)
        server.respond { request in
            request.method == "POST" ? StubResponse(status: 204) : .json(200, after)
        }

        await model.saveBiometrics(validBiometrics())

        #expect(model.onboarding == OnboardingStatus(biometricsComplete: true, goalsComplete: false, onboardingComplete: false))
        #expect(model.units == .imperial)
        #expect(model.fieldErrors.isEmpty)
        #expect(model.errorMessage == nil)
        #expect(!model.isWorking)
        #expect(server.requests.dropFirst().map(\.path) == [
            "/api/users/7/identity/onboarding/biometrics",
            "/api/users/7/identity/onboarding/status",
        ])
    }

    @Test("an invalid biometrics draft shows field errors without calling the server")
    func saveBiometricsValidatesFirst() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: false, goals: false)

        await model.saveBiometrics(BiometricsDraft())

        #expect(model.fieldErrors["heightCm"] != nil)
        #expect(model.fieldErrors["biologicalSex"] != nil)
        #expect(server.requests.count == 1)
    }

    @Test("a date of birth of today is rejected using the model's clock")
    func saveBiometricsUsesClock() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: false, goals: false)
        var draft = validBiometrics()
        draft.dateOfBirth = CalendarDate(now)

        await model.saveBiometrics(draft)

        #expect(model.fieldErrors == ["dateOfBirth": "Date of birth must be in the past"])
    }

    @Test("a server validation failure on biometrics lands on the field and keeps the step")
    func saveBiometricsServerRejects() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: false, goals: false)
        server.respond { _ in
            .json(400, #"{"status":400,"detail":"Validation failed","properties":{"errors":{"weightKg":"Weight must be 700 kg or less"}}}"#)
        }

        await model.saveBiometrics(validBiometrics())

        #expect(model.fieldErrors == ["weightKg": "Weight must be 700 kg or less"])
        #expect(model.onboarding?.biometricsComplete == false)
        #expect(model.units == .metric)
    }

    @Test("saving goals completes onboarding")
    func saveGoalsCompletes() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: false)
        let after = status(biometrics: true, goals: true)
        server.respond { request in
            request.method == "POST" ? StubResponse(status: 204) : .json(200, after)
        }
        var draft = GoalsDraft()
        draft.direction = .cut

        await model.saveGoals(draft)

        #expect(model.onboardingComplete == true)
        #expect(server.requests.dropFirst().map(\.path) == ["/api/users/7/identity/goals", "/api/users/7/identity/onboarding/status"])
    }

    @Test("a goals draft with no direction is stopped on the device")
    func saveGoalsValidatesFirst() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: false)

        await model.saveGoals(GoalsDraft())

        #expect(model.fieldErrors == ["goalDirection": "Choose one"])
        #expect(server.requests.count == 1)
    }

    @Test("a save that cannot reach the server reports the connection and keeps the step")
    func saveOffline() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: false, goals: false)
        server.respond(nil)

        await model.saveBiometrics(validBiometrics())

        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
        #expect(model.phase == .signedIn(userId: 7))
        #expect(model.onboarding?.biometricsComplete == false)
    }

    @Test("a save with a rejected token ends the session")
    func saveUnauthorized() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: false)
        server.respond { _ in .json(401, #"{"status":401}"#) }
        var draft = GoalsDraft()
        draft.direction = .bulk

        await model.saveGoals(draft)

        #expect(model.phase == .signedOut)
        #expect(model.errorMessage == "Your session ended. Sign in again.")
    }

    @Test("maintenance calories load for the goals step")
    func loadsMaintenanceCalories() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: false)
        server.respond { _ in .json(200, #"{"tdee":2630}"#) }

        await model.loadMaintenanceCalories()

        #expect(model.maintenanceCalories == 2630)
    }

    @Test("retrying after an offline launch loads the status")
    func refreshAfterOffline() async {
        let server = StubServer()
        server.respond(nil)
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(600)))
        let model = model(server, store: store)
        await model.start()
        let onboardingDone = onboardingDone
        server.respond { _ in .json(200, onboardingDone) }

        await model.refreshOnboarding()

        #expect(model.onboardingComplete == true)
        #expect(model.errorMessage == nil)
    }

    @Test("sign out forgets onboarding state")
    func signOutResetsOnboarding() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)

        await model.signOut()

        #expect(model.onboarding == nil)
        #expect(model.maintenanceCalories == nil)
    }

    // MARK: Refresh

    @Test("sign in keeps the refresh token with the access token")
    func signInStoresRefreshToken() async {
        let server = StubServer()
        let token = makeToken(userId: 10, expiresAt: now.addingTimeInterval(3600))
        let onboardingDone = onboardingDone
        server.respond { request in
            request.path.hasSuffix("/login") ? .json(200, #"{"token":"\#(token)","refreshToken":"refresh-1"}"#) : .json(200, onboardingDone)
        }
        let store = InMemoryTokenStore()
        let model = model(server, store: store)

        await model.signIn(email: "lifter@example.com", password: "a-long-test-password")

        #expect(store.read() == SessionTokens(accessToken: token, refreshToken: "refresh-1"))
    }

    @Test("launch with an expired access token and a refresh token restores the session")
    func startRefreshesExpiredSession() async {
        let server = StubServer()
        let now = now
        let fresh = makeToken(userId: 7, expiresAt: now.addingTimeInterval(3600))
        let onboardingDone = onboardingDone
        server.respond { request in
            request.path.hasSuffix("/refresh") ? .json(200, #"{"token":"\#(fresh)","refreshToken":"refresh-2"}"#) : .json(200, onboardingDone)
        }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(-3600)), refreshToken: "refresh-1")
        let model = AppModel(backend: server.backend(tokenStore: store, now: { now }), tokenStore: store, now: { now })

        await model.start()

        #expect(model.phase == .signedIn(userId: 7))
        #expect(model.onboardingComplete == true)
        #expect(model.errorMessage == nil)
        #expect(store.read() == SessionTokens(accessToken: fresh, refreshToken: "refresh-2"))
    }

    @Test("launch with a refresh token the server no longer accepts signs out")
    func startWithDeadRefreshToken() async {
        let server = StubServer()
        let now = now
        server.respond { _ in .json(401, #"{"status":401}"#) }
        let store = InMemoryTokenStore(token: makeToken(userId: 7, expiresAt: now.addingTimeInterval(-3600)), refreshToken: "refresh-1")
        let model = AppModel(backend: server.backend(tokenStore: store, now: { now }), tokenStore: store, now: { now })

        await model.start()

        #expect(model.phase == .signedOut)
        #expect(model.errorMessage == "Your session ended. Sign in again.")
        #expect(store.read() == nil)
        #expect(server.requests.map(\.path) == ["/api/identity/auth/refresh"])
    }

    @Test("sign out tells the server to end the login")
    func signOutRevokesRefreshToken() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")
        let model = model(server, store: store)

        await model.signOut()

        let request = try #require(server.requests.first)
        #expect(request.path == "/api/identity/auth/logout")
        #expect(try JSONSerialization.jsonObject(with: request.body) as? [String: String] == ["refreshToken": "refresh-1"])
        #expect(store.read() == nil)
    }

    @Test("sign out while offline still signs out on the device")
    func signOutOffline() async {
        let server = StubServer()
        server.respond(nil)
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")
        let model = model(server, store: store)

        await model.signOut()

        #expect(model.phase == .signedOut)
        #expect(model.errorMessage == nil)
        #expect(store.read() == nil)
    }

    @Test("sign out with no refresh token makes no request")
    func signOutWithoutRefreshToken() async {
        let server = StubServer()
        let model = model(server, store: InMemoryTokenStore(token: "access-1"))

        await model.signOut()

        #expect(server.requests.isEmpty)
    }
}
