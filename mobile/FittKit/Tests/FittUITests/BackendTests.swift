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

        let tokens = try await server.backend().login(credentials)

        #expect(tokens == SessionTokens(accessToken: "abc.def.ghi"))
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

        #expect(try await server.backend().register(credentials).accessToken == "abc.def.ghi")
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

    // MARK: Refresh

    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let statusBody = #"{"biometricsComplete":true,"goalsComplete":true,"onboardingComplete":true}"#

    private func pair(_ access: String, _ refresh: String) -> String {
        #"{"token":"\#(access)","refreshToken":"\#(refresh)"}"#
    }

    @Test("login returns the refresh token with the access token")
    func loginReturnsRefreshToken() async throws {
        let server = StubServer()
        let body = pair("access-1", "refresh-1")
        server.respond { _ in .json(200, body) }

        #expect(try await server.backend().login(credentials) == SessionTokens(accessToken: "access-1", refreshToken: "refresh-1"))
    }

    @Test("an expired access token is renewed before the request is sent")
    func refreshesExpiredTokenFirst() async throws {
        let server = StubServer()
        let now = now
        let expired = makeToken(userId: 10, expiresAt: now.addingTimeInterval(-60))
        let fresh = makeToken(userId: 10, expiresAt: now.addingTimeInterval(3600))
        let renewed = pair(fresh, "refresh-2")
        let statusBody = statusBody
        server.respond { request in
            request.path.hasSuffix("/refresh") ? .json(200, renewed) : .json(200, statusBody)
        }
        let store = InMemoryTokenStore(token: expired, refreshToken: "refresh-1")

        _ = try await server.backend(tokenStore: store, now: { now }).onboardingStatus(userId: 10)

        #expect(server.requests.map(\.path) == ["/api/identity/auth/refresh", "/api/users/10/identity/onboarding/status"])
        let refresh = try #require(server.requests.first)
        #expect(refresh.headers["Authorization"] == nil)
        #expect(try JSONSerialization.jsonObject(with: refresh.body) as? [String: String] == ["refreshToken": "refresh-1"])
        #expect(server.requests.last?.headers["Authorization"] == "Bearer \(fresh)")
        #expect(store.read() == SessionTokens(accessToken: fresh, refreshToken: "refresh-2"))
    }

    @Test("a 401 on a user route renews the token and sends the request once more")
    func retriesAfterUnauthorized() async throws {
        let server = StubServer()
        let renewed = pair("access-2", "refresh-2")
        let statusBody = statusBody
        server.respond { request in
            if request.path.hasSuffix("/refresh") { return .json(200, renewed) }
            return request.headers["Authorization"] == "Bearer access-2" ? .json(200, statusBody) : .json(401, #"{"status":401}"#)
        }
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")

        let status = try await server.backend(tokenStore: store).onboardingStatus(userId: 10)

        #expect(status.onboardingComplete)
        #expect(server.requests.map(\.path) == [
            "/api/users/10/identity/onboarding/status",
            "/api/identity/auth/refresh",
            "/api/users/10/identity/onboarding/status",
        ])
        #expect(store.read() == SessionTokens(accessToken: "access-2", refreshToken: "refresh-2"))
    }

    @Test("a write that gets a 401 is sent again with the same body")
    func retriesWriteWithBody() async throws {
        let server = StubServer()
        let renewed = pair("access-2", "refresh-2")
        server.respond { request in
            if request.path.hasSuffix("/refresh") { return .json(200, renewed) }
            return request.headers["Authorization"] == "Bearer access-2" ? StubResponse(status: 204) : .json(401, #"{"status":401}"#)
        }
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")

        try await server.backend(tokenStore: store).saveGoals(Goals(goalDirection: .cut, calorieGoalMode: .auto), userId: 10)

        let writes = server.requests.filter { $0.path.hasSuffix("/goals") }
        #expect(writes.count == 2)
        #expect(writes[0].body == writes[1].body)
        #expect(!writes[1].body.isEmpty)
    }

    @Test("a 401 with no refresh token is final")
    func noRefreshTokenMeansUnauthorized() async {
        let server = StubServer()
        server.respond { _ in .json(401, #"{"status":401}"#) }
        let store = InMemoryTokenStore(token: "access-1")

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).onboardingStatus(userId: 10)
        }
        #expect(server.requests.count == 1)
    }

    @Test("a request still rejected after a refresh is not sent a third time")
    func retriesOnlyOnce() async {
        let server = StubServer()
        let renewed = pair("access-2", "refresh-2")
        server.respond { request in
            request.path.hasSuffix("/refresh") ? .json(200, renewed) : .json(401, #"{"status":401}"#)
        }
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).onboardingStatus(userId: 10)
        }
        #expect(server.requests.count == 3)
    }

    @Test("a rejected refresh token ends the session and is forgotten")
    func rejectedRefreshClearsTokens() async {
        let server = StubServer()
        server.respond { _ in .json(401, #"{"status":401,"title":"Invalid Refresh Token"}"#) }
        let store = InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).onboardingStatus(userId: 10)
        }
        #expect(store.read() == nil)
        #expect(server.requests.map(\.path) == ["/api/users/10/identity/onboarding/status", "/api/identity/auth/refresh"])
    }

    @Test("a refresh that cannot reach the server keeps the tokens for a later try")
    func offlineRefreshKeepsTokens() async {
        let server = StubServer()
        server.respond(nil)
        let now = now
        let expired = makeToken(userId: 10, expiresAt: now.addingTimeInterval(-60))
        let store = InMemoryTokenStore(token: expired, refreshToken: "refresh-1")

        await #expect(throws: BackendError.transport) {
            try await server.backend(tokenStore: store, now: { now }).onboardingStatus(userId: 10)
        }
        #expect(store.read() == SessionTokens(accessToken: expired, refreshToken: "refresh-1"))
    }

    @Test("requests that find the token expired together share one refresh")
    func concurrentRequestsRefreshOnce() async throws {
        let server = StubServer()
        let now = now
        let expired = makeToken(userId: 10, expiresAt: now.addingTimeInterval(-60))
        let fresh = makeToken(userId: 10, expiresAt: now.addingTimeInterval(3600))
        let renewed = pair(fresh, "refresh-2")
        let statusBody = statusBody
        // A second use of refresh-1 fails, as it does on the real server.
        let used = Locked(false)
        server.respond { request in
            guard request.path.hasSuffix("/refresh") else { return .json(200, statusBody) }
            let alreadyUsed = used.withLock { value in
                defer { value = true }
                return value
            }
            return alreadyUsed ? .json(401, #"{"status":401}"#) : .json(200, renewed)
        }
        let backend = server.backend(tokenStore: InMemoryTokenStore(token: expired, refreshToken: "refresh-1"), now: { now })

        try await withThrowingTaskGroup(of: Void.self) { group in
            for _ in 0..<8 {
                group.addTask { _ = try await backend.onboardingStatus(userId: 10) }
            }
            try await group.waitForAll()
        }

        #expect(server.requests.filter { $0.path.hasSuffix("/refresh") }.count == 1)
        #expect(server.requests.filter { $0.path.hasSuffix("/status") }.count == 8)
    }

    @Test("logout posts the refresh token and no access token")
    func logoutPostsRefreshToken() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend(tokenStore: InMemoryTokenStore(token: "access-1", refreshToken: "refresh-1")).logout(refreshToken: "refresh-1")

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/identity/auth/logout")
        #expect(request.headers["Authorization"] == nil)
        #expect(try JSONSerialization.jsonObject(with: request.body) as? [String: String] == ["refreshToken": "refresh-1"])
    }

    // MARK: Workouts

    private let planBody = #"{"id":3,"ownerUserId":10,"name":"Full Body","daysPerWeek":3,"splitType":"FULL_BODY","active":true,"days":[]}"#

    @Test("next lift is read from the next-lift route")
    func readsNextLift() async throws {
        let server = StubServer()
        server.respond { _ in
            .json(200, #"{"hasActivePlan":false,"planId":null,"planName":null,"daysInRotation":0,"dayNumber":0,"weekInCycle":1,"deload":false,"alreadyTrainedToday":false,"day":null}"#)
        }

        let next = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).nextLift(userId: 10)

        #expect(next == NextLift(hasActivePlan: false))
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/next-lift")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("plans are read as a list")
    func readsPlans() async throws {
        let server = StubServer()
        let planBody = planBody
        server.respond { _ in .json(200, "[\(planBody)]") }

        let plans = try await server.backend().workoutPlans(userId: 10)

        #expect(plans == [WorkoutPlan(id: 3, name: "Full Body", daysPerWeek: 3, splitType: .fullBody, active: true, days: [])])
        #expect(server.requests.first?.path == "/api/users/10/workout-plans")
    }

    @Test("activating a plan posts to its activate route with no body")
    func activatesPlan() async throws {
        let server = StubServer()
        let planBody = planBody
        server.respond { _ in .json(200, planBody) }

        let plan = try await server.backend().activatePlan(3, userId: 10)

        #expect(plan.active)
        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/workout-plans/3/activate")
        #expect(request.body.isEmpty)
        #expect(request.headers["Content-Type"] == nil)
    }

    @Test("exercises are read as a list")
    func readsExercises() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"[{"id":38,"name":"Back Squat","muscleGroup":"LEGS","custom":false}]"#) }

        let exercises = try await server.backend().exercises(userId: 10)

        #expect(exercises == [Exercise(id: 38, name: "Back Squat", muscleGroup: .legs, custom: false)])
        #expect(server.requests.first?.path == "/api/users/10/exercises")
    }

    @Test("updating a plan puts the whole plan to its own path, and accepts the server's answer with null ids")
    func updatesPlan() async throws {
        let server = StubServer()
        // What the server really sends: the rebuilt day has no id yet.
        server.respond { _ in
            .json(200, #"{"id":9,"ownerUserId":10,"name":"Renamed","daysPerWeek":3,"splitType":"FULL_BODY","active":true,"days":[{"id":null,"orderIndex":0,"dayLabel":"Full Body","exercises":[]}]}"#)
        }
        var draft = PlanDraft(splitType: .fullBody)
        draft.name = "Renamed"

        try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).updatePlan(draft.plan(), planId: 9, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "PUT")
        #expect(request.path == "/api/users/10/workout-plans/9")
        #expect(request.headers["Authorization"] == "Bearer stored")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        #expect(body["name"] as? String == "Renamed")
    }

    @Test("deleting a plan sends DELETE to its path and accepts a 204")
    func deletesPlan() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).deletePlan(9, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "DELETE")
        #expect(request.path == "/api/users/10/workout-plans/9")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("creating a plan posts the plan and accepts a 201 with the created plan")
    func createsPlan() async throws {
        let server = StubServer()
        server.respond { _ in
            .json(201, #"{"id":9,"ownerUserId":10,"name":"Full Body","daysPerWeek":3,"splitType":"FULL_BODY","active":false,"days":[]}"#)
        }
        var draft = PlanDraft(splitType: .fullBody)
        draft.name = "Full Body"

        let plan = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).createPlan(draft.plan(), userId: 10)

        #expect(plan.id == 9)
        #expect(!plan.active)
        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/workout-plans")
        #expect(request.headers["Authorization"] == "Bearer stored")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        #expect(body["name"] as? String == "Full Body")
        #expect(body["splitType"] as? String == "FULL_BODY")
        #expect((body["days"] as? [[String: Any]])?.first?["dayLabel"] as? String == "Full Body")
    }

    @Test("deleting an exercise sends DELETE with no body and accepts a 204")
    func deletesExercise() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend().deleteExercise(71, userId: 10)

        let sent = try #require(server.requests.first)
        #expect(sent.method == "DELETE")
        #expect(sent.path == "/api/users/10/exercises/71")
        #expect(sent.body.isEmpty)
    }

    @Test("creating an exercise posts the name and muscle group and accepts a 201 with the exercise")
    func createsExercise() async throws {
        let server = StubServer()
        server.respond { _ in .json(201, #"{"id":71,"name":"Landmine Press","muscleGroup":"SHOULDERS","custom":true}"#) }
        let request = try ExerciseDraft(name: "Landmine Press", muscleGroup: .shoulders).exercise()

        let exercise = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).createExercise(request, userId: 10)

        #expect(exercise == Exercise(id: 71, name: "Landmine Press", muscleGroup: .shoulders, custom: true))
        let sent = try #require(server.requests.first)
        #expect(sent.method == "POST")
        #expect(sent.path == "/api/users/10/exercises")
        #expect(sent.headers["Authorization"] == "Bearer stored")
        let body = try #require(try JSONSerialization.jsonObject(with: sent.body) as? [String: String])
        #expect(body == ["name": "Landmine Press", "muscleGroup": "SHOULDERS"])
    }

    // MARK: Sessions

    private let sessionBody = #"{"id":12,"ownerUserId":10,"planId":5,"startedAt":"2026-11-16T05:20:32Z","finishedAt":null,"notes":null,"exerciseEntries":[]}"#

    /// The method and path of the single request a call made, and its JSON body.
    private func sent(_ server: StubServer) throws -> (line: String, body: [String: Any]) {
        let request = try #require(server.requests.last)
        let body = request.body.isEmpty ? [:] : try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        return ("\(request.method) \(request.path)", body)
    }

    @Test("sessions are read as a list")
    func readsSessions() async throws {
        let server = StubServer()
        let sessionBody = sessionBody
        server.respond { _ in .json(200, "[\(sessionBody)]") }

        #expect(try await server.backend().sessions(userId: 10).map(\.id) == [12])
        #expect(try sent(server).line == "GET /api/users/10/workout-sessions")
    }

    @Test("starting a session posts the start time and plan")
    func startsSession() async throws {
        let server = StubServer()
        let sessionBody = sessionBody
        server.respond { _ in .json(201, sessionBody) }

        let session = try await server.backend().startSession(
            NewWorkoutSession(startedAt: Date(timeIntervalSince1970: 1_794_806_432), planId: 5), userId: 10)

        #expect(session.id == 12)
        let request = try sent(server)
        #expect(request.line == "POST /api/users/10/workout-sessions")
        #expect(request.body["startedAt"] as? String == "2026-11-16T05:20:32Z")
        #expect(request.body["planId"] as? Int == 5)
    }

    @Test("adding an entry posts the exercise id")
    func addsEntry() async throws {
        let server = StubServer()
        let sessionBody = sessionBody
        server.respond { _ in .json(200, sessionBody) }

        _ = try await server.backend().addEntry(exerciseId: 38, sessionId: 12, userId: 10)

        let request = try sent(server)
        #expect(request.line == "POST /api/users/10/workout-sessions/12/entries")
        #expect(request.body["exerciseId"] as? Int == 38)
    }

    @Test("logging a set posts reps, weight and RPE to the entry")
    func logsSet() async throws {
        let server = StubServer()
        let sessionBody = sessionBody
        server.respond { _ in .json(200, sessionBody) }

        _ = try await server.backend().logSet(
            LoggedSet(reps: 8, weight: Decimal(string: "102.5"), rpe: Decimal(string: "8.5")), entryId: 30, sessionId: 12, userId: 10)

        let request = try sent(server)
        #expect(request.line == "POST /api/users/10/workout-sessions/12/entries/30/sets")
        #expect(request.body["reps"] as? Int == 8)
        #expect(request.body["weight"] as? Double == 102.5)
        #expect(request.body["rpe"] as? Double == 8.5)
    }

    @Test("deleting a set, finishing and deleting a session use their routes")
    func sessionRoutes() async throws {
        let server = StubServer()
        let sessionBody = sessionBody
        server.respond { request in
            request.method == "DELETE" && request.path.hasSuffix("/12") ? StubResponse(status: 204) : .json(200, sessionBody)
        }
        let backend = server.backend()

        _ = try await backend.deleteSet(101, entryId: 30, sessionId: 12, userId: 10)
        #expect(try sent(server).line == "DELETE /api/users/10/workout-sessions/12/entries/30/sets/101")

        _ = try await backend.removeEntry(30, sessionId: 12, userId: 10)
        #expect(try sent(server).line == "DELETE /api/users/10/workout-sessions/12/entries/30")
        #expect(server.requests.last?.body.isEmpty == true)

        _ = try await backend.finishSession(12, userId: 10)
        #expect(try sent(server).line == "POST /api/users/10/workout-sessions/12/finish")
        #expect(server.requests.last?.body.isEmpty == true)

        try await backend.deleteSession(12, userId: 10)
        #expect(try sent(server).line == "DELETE /api/users/10/workout-sessions/12")
    }

    // MARK: Progression

    @Test("a suggestion is read for one exercise, named in the query")
    func readsSuggestion() async throws {
        let server = StubServer()
        server.respond { _ in
            .json(200, #"{"exerciseId":38,"modelUsed":"DOUBLE_PROGRESSION","suggestedWeight":"102.50","suggestedReps":8,"suggestedSets":3,"reasoning":"Increase weight."}"#)
        }

        let suggestion = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).suggestion(exerciseId: 38, userId: 10)

        #expect(suggestion.suggestedWeight == Decimal(string: "102.5"))
        #expect(suggestion.suggestedReps == 8)
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/progression/suggestions")
        #expect(request.query == "exerciseId=38")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("the progression model is read from a bare JSON string")
    func readsProgressionModel() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #""RPE_AUTOREGULATION""#) }

        let model = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).progressionModel(userId: 10)

        #expect(model == .rpeAutoregulation)
        #expect(server.requests.first?.path == "/api/users/10/progression/preference")
    }

    @Test("changing the progression model puts the model and accepts a 204")
    func setsProgressionModel() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).setProgressionModel(.linearPeriodization, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "PUT")
        #expect(request.path == "/api/users/10/progression/preference")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: String])
        #expect(body == ["progressionModel": "LINEAR_PERIODIZATION"])
    }

    // MARK: Body weight

    @Test("weight entries are read with the days sent as a query, not as part of the path")
    func readsBodyWeights() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"[{"id":9,"loggedAt":"2026-11-16","weight":82.5,"createdAt":"2026-11-16T05:20:32Z"}]"#) }

        let entries = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).bodyWeights(userId: 10, days: 90)

        #expect(entries == [BodyWeightEntry(id: 9, loggedAt: CalendarDate(year: 2026, month: 11, day: 16), weight: Decimal(string: "82.5")!)])
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/measurements/weight")
        #expect(request.query == "days=90")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }

    @Test("a request with no query items has no query string")
    func noQueryByDefault() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"average":81.7}"#) }

        #expect(try await server.backend().bodyWeightAverage(userId: 10) == Decimal(string: "81.7"))
        let request = try #require(server.requests.first)
        #expect(request.path == "/api/users/10/measurements/weight/average")
        #expect(request.query == nil)
    }

    @Test("logging a weight posts the day and weight, and accepts both 201 and 200", arguments: [201, 200])
    func logsBodyWeight(status: Int) async throws {
        let server = StubServer()
        server.respond { _ in .json(status, #"{"id":9,"loggedAt":"2026-11-16","weight":82.5,"createdAt":"2026-11-16T05:20:32Z"}"#) }
        let entry = NewBodyWeight(loggedAt: CalendarDate(year: 2026, month: 11, day: 16), weight: Decimal(string: "82.5")!)

        #expect(try await server.backend().logBodyWeight(entry, userId: 10).id == 9)

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/measurements/weight")
        let body = try #require(try JSONSerialization.jsonObject(with: request.body) as? [String: Any])
        #expect(body["loggedAt"] as? String == "2026-11-16")
        #expect(body["weight"] as? Double == 82.5)
    }

    @Test("deleting a weight entry uses its route and accepts a 204")
    func deletesBodyWeight() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 204) }

        try await server.backend().deleteBodyWeight(9, userId: 10)

        let request = try #require(server.requests.first)
        #expect(request.method == "DELETE")
        #expect(request.path == "/api/users/10/measurements/weight/9")
    }

    @Test("saved goals are read from the goals route")
    func readsGoals() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, #"{"goalDirection":"CUT","targetWeightKg":81.65,"timelineWeeks":null,"calorieGoalMode":"AUTO","calorieGoal":2235}"#) }

        let goals = try await server.backend(tokenStore: InMemoryTokenStore(token: "stored")).goals(userId: 10)

        #expect(goals == Goals(goalDirection: .cut, targetWeightKg: Decimal(string: "81.65")!, calorieGoalMode: .auto, calorieGoal: 2235))
        let request = try #require(server.requests.first)
        #expect(request.method == "GET")
        #expect(request.path == "/api/users/10/identity/goals")
        #expect(request.headers["Authorization"] == "Bearer stored")
    }
}
