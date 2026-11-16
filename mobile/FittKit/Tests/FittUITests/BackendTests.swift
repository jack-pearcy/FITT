import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("Backend")
struct BackendTests {
    private let credentials = Credentials(email: "lifter@example.com", password: "a-long-test-password")

    @Test("login posts the credentials as JSON and returns the token")
    func loginSendsCredentials() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"token":"abc.def.ghi"}"#) }

        let token = try await server.backend().login(credentials)

        #expect(token == "abc.def.ghi")
        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/identity/auth/login")
        #expect(request.headers["Content-Type"] == "application/json")
        let body = try JSONSerialization.jsonObject(with: request.body) as? [String: String]
        #expect(body == ["email": "lifter@example.com", "password": "a-long-test-password"])
    }

    @Test("register posts to the register route and accepts a 201")
    func registerUsesRegisterRoute() async throws {
        let server = StubServer()
        server.respond { _ in .json(201, #"{"token":"abc.def.ghi"}"#) }

        #expect(try await server.backend().register(credentials) == "abc.def.ghi")
        #expect(server.requests.first?.path == "/api/identity/auth/register")
    }

    @Test("auth routes never carry a stored token")
    func authRoutesSkipToken() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"token":"new"}"#) }

        _ = try await server.backend(tokenStore: InMemoryTokenStore(token: "stale")).login(credentials)

        #expect(server.requests.first?.headers["Authorization"] == nil)
    }

    @Test("user routes carry the stored token as a bearer header")
    func userRoutesSendToken() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"biometricsComplete":true,"goalsComplete":false,"onboardingComplete":false}"#) }

        let status = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).onboardingStatus(userId: 10)

        #expect(status == OnboardingStatus(biometricsComplete: true, goalsComplete: false, onboardingComplete: false))
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/identity/onboarding/status")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("every request carries an X-Request-ID the server will accept")
    func sendsRequestId() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"token":"t"}"#) }

        _ = try await server.backend().login(credentials)
        _ = try await server.backend().login(credentials)

        let ids = server.requests.compactMap { $0.headers["X-Request-ID"] }
        #expect(ids.count == 2)
        #expect(Set(ids).count == 2)
        // The server accepts 1 to 64 characters of letters, digits, dot, underscore, hyphen.
        for id in ids {
            #expect(id.wholeMatch(of: /[A-Za-z0-9._-]{1,64}/) != nil)
        }
    }

    @Test("a 401 becomes unauthorized")
    func mapsUnauthorized() async {
        let server = StubServer()
        server.respond { _ in .json(401, #"{"title":"Bad Credentials","status":401,"detail":"Bad credentials"}"#) }

        await #expect(throws: BackendError.unauthorized) { try await server.backend().login(credentials) }
    }

    @Test("a 400 carries the per-field messages")
    func mapsValidationProblem() async {
        let server = StubServer()
        server.respond { _ in
            .json(400, #"{"title":"Bad Request","status":400,"detail":"Validation failed","properties":{"errors":{"email":"Must be a valid email address"}}}"#)
        }

        let expected = BackendError.problem(status: 400, APIProblem(
            status: 400, title: "Bad Request", detail: "Validation failed",
            fieldErrors: ["email": "Must be a valid email address"]
        ))
        await #expect(throws: expected) { try await server.backend().register(credentials) }
    }

    @Test("a 429 carries the Retry-After seconds")
    func mapsRateLimit() async {
        let server = StubServer()
        server.respond { _ in
            StubResponse(status: 429, headers: ["Retry-After": "42", "Content-Type": "application/json"], body: #"{"title":"Too Many Requests","status":429}"#)
        }

        await #expect(throws: BackendError.rateLimited(retryAfter: 42)) { try await server.backend().login(credentials) }
    }

    @Test("a 5xx without a JSON body becomes unexpectedStatus")
    func mapsUnreadableError() async {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 502, body: "<html>Bad Gateway</html>") }

        await #expect(throws: BackendError.unexpectedStatus(502)) { try await server.backend().login(credentials) }
    }

    @Test("a 2xx with the wrong shape becomes undecodableResponse")
    func mapsBadSuccessBody() async {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"unexpected":true}"#) }

        await #expect(throws: BackendError.undecodableResponse) { try await server.backend().login(credentials) }
    }

    @Test("a connection failure becomes transport")
    func mapsTransportFailure() async {
        let server = StubServer()
        server.respond(nil)

        await #expect(throws: BackendError.transport) { try await server.backend().login(credentials) }
    }

    @Test("saving biometrics posts the metric body with the token and accepts a 204")
    func savesBiometrics() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }
        let biometrics = Biometrics(
            heightCm: 180,
            weightKg: Decimal(string: "82.5")!,
            dateOfBirth: CalendarDate(year: 1994, month: 3, day: 7),
            biologicalSex: .female,
            activityLevel: .veryActive,
            unitsPreference: .imperial
        )

        try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).saveBiometrics(biometrics, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/identity/onboarding/biometrics")
        #expect(request.headers["Authorization"] == "Bearer stored")
        #expect(request.headers["Content-Type"] == "application/json")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        #expect(body["heightCm"] as? Double == 180)
        #expect(body["weightKg"] as? Double == 82.5)
        #expect(body["dateOfBirth"] as? String == "1994-03-07")
        #expect(body["biologicalSex"] as? String == "FEMALE")
        #expect(body["activityLevel"] as? String == "VERY_ACTIVE")
        #expect(body["unitsPreference"] as? String == "IMPERIAL")
    }

    @Test("saving goals posts to the goals route and accepts a 204")
    func savesGoals() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend().saveGoals(Goals(goalDirection: .cut, timelineWeeks: 12, calorieGoalMode: .manual, calorieGoal: 2200), userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/identity/goals")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        #expect(body.keys.sorted() == ["calorieGoal", "calorieGoalMode", "goalDirection", "timelineWeeks"])
        #expect(body["calorieGoal"] as? Int == 2200)
    }

    @Test("a rejected save throws the server's problem")
    func saveRejected() async {
        let server = StubServer()
        server.respond { _ in .json(404, #"{"status":404,"detail":"biometrics not found for user 10"}"#) }

        await #expect(throws: BackendError.problem(status: 404, APIProblem(status: 404, title: nil, detail: "biometrics not found for user 10", fieldErrors: [:]))) {
            try await server.backend().saveGoals(Goals(goalDirection: .cut, calorieGoalMode: .auto), userId: 10)
        }
    }

    @Test("maintenance calories come from the tdee route")
    func readsMaintenanceCalories() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"tdee":2630}"#) }

        #expect(try await server.backend().maintenanceCalories(userId: 10) == 2630)
        #expect(server.requests.first?.path == "/api/users/10/identity/onboarding/tdee")
    }
}
