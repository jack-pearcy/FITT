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
        #expect(store.read() == token)
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
            func read() throws -> String? { nil }
            func save(_ token: String) throws { throw KeychainError(status: -34018) }
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

        model.signOut()

        #expect(model.phase == .signedOut)
        #expect(store.read() == nil)
    }
}
