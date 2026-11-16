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

    // MARK: Workouts

    private func planJSON(id: Int, name: String, active: Bool) -> String {
        #"{"id":\#(id),"ownerUserId":7,"name":"\#(name)","daysPerWeek":3,"splitType":"FULL_BODY","active":\#(active),"days":[]}"#
    }

    private func nextLiftJSON(planId: Int, planName: String) -> String {
        #"{"hasActivePlan":true,"planId":\#(planId),"planName":"\#(planName)","daysInRotation":0,"dayNumber":0,"weekInCycle":1,"deload":false,"alreadyTrainedToday":false,"day":null}"#
    }

    @Test("next lift loads for the signed-in user")
    func loadsNextLift() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let body = nextLiftJSON(planId: 3, planName: "Full Body")
        server.respond { _ in .json(200, body) }

        await model.loadNextLift()

        #expect(model.nextLift == NextLift(hasActivePlan: true, planId: 3, planName: "Full Body"))
        #expect(server.requests.last?.path == "/api/users/7/next-lift")
    }

    @Test("plans load for the signed-in user")
    func loadsPlans() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let body = "[\(planJSON(id: 3, name: "Full Body", active: true))]"
        server.respond { _ in .json(200, body) }

        await model.loadPlans()

        #expect(model.plans?.map(\.name) == ["Full Body"])
    }

    @Test("a user with no plans gets an empty list, not a missing one")
    func loadsNoPlans() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in .json(200, "[]") }

        await model.loadPlans()

        #expect(model.plans == [])
    }

    @Test("a failed load keeps what was already shown and reports the connection")
    func loadFailureKeepsData() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in .json(200, "[]") }
        await model.loadPlans()
        server.respond(nil)

        await model.loadPlans()

        #expect(model.plans == [])
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
    }

    @Test("activating a plan reloads the plans and what to train next")
    func activatePlanReloads() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let activated = planJSON(id: 4, name: "Push Pull", active: true)
        let plans = "[\(planJSON(id: 3, name: "Full Body", active: false)),\(activated)]"
        let next = nextLiftJSON(planId: 4, planName: "Push Pull")
        server.respond { request in
            if request.method == "POST" { return .json(200, activated) }
            return request.path.hasSuffix("/next-lift") ? .json(200, next) : .json(200, plans)
        }

        await model.activatePlan(4)

        #expect(model.plans?.first(where: \.active)?.id == 4)
        #expect(model.nextLift?.planId == 4)
        #expect(!model.isWorking)
        #expect(server.requests.dropFirst().map(\.path) == [
            "/api/users/7/workout-plans/4/activate",
            "/api/users/7/workout-plans",
            "/api/users/7/next-lift",
        ])
    }

    @Test("a rejected activation shows the server's detail and reloads nothing")
    func activatePlanRejected() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in .json(404, #"{"status":404,"title":"Workout Plan Not Found","detail":"Workout plan not found"}"#) }

        await model.activatePlan(99)

        #expect(model.errorMessage == "Workout plan not found")
        #expect(server.requests.count == 2)
    }

    @Test("sign out forgets plans and next lift")
    func signOutResetsWorkouts() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in .json(200, "[]") }
        await model.loadPlans()

        await model.signOut()

        #expect(model.plans == nil)
        #expect(model.nextLift == nil)
    }

    // MARK: Creating a plan

    private func namedDraft(_ name: String = "Full Body") -> PlanDraft {
        var draft = PlanDraft(splitType: .fullBody)
        draft.name = name
        return draft
    }

    @Test("exercises load for the picker")
    func loadsExercises() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in .json(200, #"[{"id":38,"name":"Back Squat","muscleGroup":"LEGS","custom":false}]"#) }

        await model.loadExercises()

        #expect(model.exercises?.map(\.name) == ["Back Squat"])
    }

    @Test("a first plan is created, made active, and shown")
    func createFirstPlanActivatesIt() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let created = planJSON(id: 9, name: "Full Body", active: false)
        let activated = planJSON(id: 9, name: "Full Body", active: true)
        let next = nextLiftJSON(planId: 9, planName: "Full Body")
        let isActive = Locked(false)
        server.respond { request in
            if request.path.hasSuffix("/activate") {
                isActive.withLock { $0 = true }
                return .json(200, activated)
            }
            if request.method == "POST" { return .json(201, created) }
            if request.path.hasSuffix("/next-lift") { return .json(200, next) }
            return .json(200, "[\(isActive.withLock { $0 } ? activated : created)]")
        }

        let saved = await model.createPlan(namedDraft())

        #expect(saved)
        #expect(model.plans?.map(\.active) == [true])
        #expect(model.nextLift?.planId == 9)
        #expect(model.errorMessage == nil)
        #expect(!model.isWorking)
        #expect(server.requests.dropFirst().map { "\($0.method) \($0.path)" } == [
            "POST /api/users/7/workout-plans",
            "GET /api/users/7/workout-plans",
            "POST /api/users/7/workout-plans/9/activate",
            "GET /api/users/7/workout-plans",
            "GET /api/users/7/next-lift",
        ])
    }

    @Test("a plan created next to an active one is left inactive")
    func createSecondPlanDoesNotActivate() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let created = planJSON(id: 9, name: "Full Body", active: false)
        let plans = "[\(planJSON(id: 3, name: "Upper Lower", active: true)),\(created)]"
        let next = nextLiftJSON(planId: 3, planName: "Upper Lower")
        server.respond { request in
            if request.method == "POST" { return .json(201, created) }
            return request.path.hasSuffix("/next-lift") ? .json(200, next) : .json(200, plans)
        }

        let saved = await model.createPlan(namedDraft())

        #expect(saved)
        #expect(model.plans?.first(where: \.active)?.id == 3)
        #expect(!server.requests.contains { $0.path.hasSuffix("/activate") })
    }

    @Test("a draft with no name is stopped on the device")
    func createPlanValidatesFirst() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)

        let saved = await model.createPlan(PlanDraft(splitType: .fullBody))

        #expect(!saved)
        #expect(model.fieldErrors == ["name": "Plan name is required"])
        #expect(server.requests.count == 1)
    }

    @Test("a server validation failure keeps the form open with the field message")
    func createPlanServerRejects() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond { _ in
            .json(400, #"{"status":400,"detail":"Validation failed","properties":{"errors":{"days[0].dayLabel":"Day label is required"}}}"#)
        }

        let saved = await model.createPlan(namedDraft())

        #expect(!saved)
        #expect(model.fieldErrors == ["days[0].dayLabel": "Day label is required"])
        #expect(model.plans == nil)
    }

    @Test("a create that cannot reach the server keeps the form open")
    func createPlanOffline() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        server.respond(nil)

        let saved = await model.createPlan(namedDraft())

        #expect(!saved)
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
    }

    @Test("a plan that saves but cannot be activated still closes the form and says why")
    func createPlanActivationFails() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let created = planJSON(id: 9, name: "Full Body", active: false)
        let next = #"{"hasActivePlan":false,"planId":null,"planName":null,"daysInRotation":0,"dayNumber":0,"weekInCycle":1,"deload":false,"alreadyTrainedToday":false,"day":null}"#
        server.respond { request in
            if request.path.hasSuffix("/activate") { return .json(500, #"{"status":500,"detail":"Unexpected error"}"#) }
            if request.method == "POST" { return .json(201, created) }
            return request.path.hasSuffix("/next-lift") ? .json(200, next) : .json(200, "[\(created)]")
        }

        let saved = await model.createPlan(namedDraft())

        #expect(saved)
        #expect(model.plans?.map(\.id) == [9])
        #expect(model.errorMessage == "Unexpected error")
    }

    // MARK: Workout in progress

    private let squat = #"{"id":38,"name":"Back Squat","muscleGroup":"LEGS","custom":false}"#
    private let bench = #"{"id":1,"name":"Barbell Bench Press","muscleGroup":"CHEST","custom":false}"#

    /// Next lift for plan 5 whose day has bench (first) then squat, sent out of order.
    private var nextLiftWithDay: String {
        #"{"hasActivePlan":true,"planId":5,"planName":"Full Body","daysInRotation":1,"dayNumber":1,"weekInCycle":1,"deload":false,"alreadyTrainedToday":false,"day":{"id":20,"orderIndex":0,"dayLabel":"Day A","exercises":[{"id":202,"orderIndex":1,"sets":3,"reps":5,"exercise":\#(squat)},{"id":201,"orderIndex":0,"sets":3,"reps":5,"exercise":\#(bench)}]}}"#
    }

    private func sessionJSON(finished: Bool = false, entries: String = "") -> String {
        let finishedAt = finished ? #""2026-11-16T06:00:00Z""# : "null"
        return #"{"id":12,"ownerUserId":7,"planId":5,"startedAt":"2026-11-16T05:20:32Z","finishedAt":\#(finishedAt),"notes":null,"exerciseEntries":[\#(entries)]}"#
    }

    private func entryJSON(id: Int, order: Int, exercise: String, sets: String = "") -> String {
        #"{"id":\#(id),"orderIndex":\#(order),"exercise":\#(exercise),"sets":[\#(sets)]}"#
    }

    /// A signed-in model with next lift loaded and, optionally, a session in progress.
    private func workoutModel(_ server: StubServer, inProgress: Bool) async -> AppModel {
        let model = await signedInModel(server, biometrics: true, goals: true)
        let next = nextLiftWithDay
        let session = sessionJSON(entries: entryJSON(id: 30, order: 0, exercise: bench))
        server.respond { request in
            request.path.hasSuffix("/next-lift") ? .json(200, next) : .json(200, inProgress ? "[\(session)]" : "[]")
        }
        await model.loadNextLift()
        await model.loadActiveSession()
        return model
    }

    private func calls(_ server: StubServer, after count: Int) -> [String] {
        server.requests.dropFirst(count).map { "\($0.method) \($0.path)" }
    }

    @Test("an unfinished session is picked up as the workout in progress")
    func loadsActiveSession() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let finished = sessionJSON(finished: true).replacingOccurrences(of: #""id":12"#, with: #""id":11"#)
        let open = sessionJSON()
        server.respond { _ in .json(200, "[\(finished),\(open)]") }

        await model.loadActiveSession()

        #expect(model.activeSession?.id == 12)
    }

    @Test("with every session finished there is no workout in progress")
    func noActiveSession() async {
        let server = StubServer()
        let model = await signedInModel(server, biometrics: true, goals: true)
        let finished = sessionJSON(finished: true)
        server.respond { _ in .json(200, "[\(finished)]") }

        await model.loadActiveSession()

        #expect(model.activeSession == nil)
    }

    @Test("starting a workout creates the session, then adds the day's exercises in plan order")
    func startWorkout() async throws {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: false)
        let before = server.requests.count
        let empty = sessionJSON()
        let withBoth = sessionJSON(entries: [entryJSON(id: 30, order: 0, exercise: bench), entryJSON(id: 31, order: 1, exercise: squat)].joined(separator: ","))
        server.respond { request in
            request.path.hasSuffix("/entries") ? .json(200, withBoth) : .json(201, empty)
        }

        await model.startWorkout()

        #expect(model.activeSession?.orderedEntries.map(\.exercise.name) == ["Barbell Bench Press", "Back Squat"])
        #expect(model.errorMessage == nil)
        #expect(!model.isWorking)
        #expect(calls(server, after: before) == [
            "POST /api/users/7/workout-sessions",
            "POST /api/users/7/workout-sessions/12/entries",
            "POST /api/users/7/workout-sessions/12/entries",
        ])
        let sent = try server.requests.dropFirst(before).map {
            try #require(try JSONSerialization.jsonObject(with: $0.body) as? [String: Any])
        }
        #expect(sent[0]["planId"] as? Int == 5)
        #expect(sent[0]["startedAt"] as? String == "2027-01-15T08:00:00Z")
        #expect(sent[1]["exerciseId"] as? Int == 1)
        #expect(sent[2]["exerciseId"] as? Int == 38)
    }

    @Test("a start that fails partway keeps the session that was created")
    func startWorkoutPartialFailure() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: false)
        let empty = sessionJSON()
        server.respond { request in
            request.path.hasSuffix("/entries") ? .json(500, #"{"status":500,"detail":"Unexpected error"}"#) : .json(201, empty)
        }

        await model.startWorkout()

        #expect(model.activeSession?.id == 12)
        #expect(model.activeSession?.exerciseEntries.isEmpty == true)
        #expect(model.errorMessage == "Unexpected error")
    }

    @Test("starting does nothing while a workout is already in progress")
    func startWorkoutWhenActive() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count

        await model.startWorkout()

        #expect(server.requests.count == before)
    }

    @Test("logging a set saves it and shows the updated session")
    func logsSet() async throws {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count
        let updated = sessionJSON(entries: entryJSON(id: 30, order: 0, exercise: bench, sets: #"{"id":101,"setNumber":1,"reps":8,"weight":100.00,"rpe":null,"completed":true}"#))
        server.respond { _ in .json(200, updated) }

        let saved = await model.logSet(SetDraft(weight: "100", reps: "8"), entryId: 30)

        #expect(saved)
        #expect(model.activeSession?.loggedSetCount == 1)
        #expect(model.fieldErrors.isEmpty)
        #expect(calls(server, after: before) == ["POST /api/users/7/workout-sessions/12/entries/30/sets"])
    }

    @Test("a set with no reps is stopped on the device, with the message filed under its exercise")
    func logSetValidatesFirst() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count

        let saved = await model.logSet(SetDraft(weight: "100"), entryId: 30)

        #expect(!saved)
        #expect(model.fieldErrors == ["entry.30.reps": "Enter reps from 1 to 1000"])
        #expect(server.requests.count == before)
    }

    @Test("a server rejection of a set is filed under its exercise too")
    func logSetServerRejects() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        server.respond { _ in
            .json(400, #"{"status":400,"detail":"Validation failed","properties":{"errors":{"weight":"Weight must be 9999.99 or less"}}}"#)
        }

        let saved = await model.logSet(SetDraft(weight: "100", reps: "8"), entryId: 30)

        #expect(!saved)
        #expect(model.fieldErrors == ["entry.30.weight": "Weight must be 9999.99 or less"])
        #expect(model.activeSession?.loggedSetCount == 0)
    }

    @Test("deleting a set shows the session the server returns")
    func deletesSet() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count
        let updated = sessionJSON(entries: entryJSON(id: 30, order: 0, exercise: bench))
        server.respond { _ in .json(200, updated) }

        await model.deleteSet(101, entryId: 30)

        #expect(model.activeSession?.loggedSetCount == 0)
        #expect(calls(server, after: before) == ["DELETE /api/users/7/workout-sessions/12/entries/30/sets/101"])
    }

    @Test("finishing ends the workout and reloads what comes next")
    func finishWorkout() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count
        let finished = sessionJSON(finished: true)
        let next = nextLiftWithDay.replacingOccurrences(of: #""alreadyTrainedToday":false"#, with: #""alreadyTrainedToday":true"#)
        server.respond { request in
            request.path.hasSuffix("/finish") ? .json(200, finished) : .json(200, next)
        }

        await model.finishWorkout()

        #expect(model.activeSession == nil)
        #expect(model.nextLift?.alreadyTrainedToday == true)
        #expect(!model.isWorking)
        #expect(calls(server, after: before) == ["POST /api/users/7/workout-sessions/12/finish", "GET /api/users/7/next-lift"])
    }

    @Test("a finish that cannot reach the server keeps the workout open")
    func finishWorkoutOffline() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        server.respond(nil)

        await model.finishWorkout()

        #expect(model.activeSession?.id == 12)
        #expect(model.errorMessage == "Cannot reach the server. Check the connection.")
    }

    @Test("discarding deletes the session")
    func discardWorkout() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        let before = server.requests.count
        server.respond { _ in StubResponse(status: 204) }

        await model.discardWorkout()

        #expect(model.activeSession == nil)
        #expect(calls(server, after: before) == ["DELETE /api/users/7/workout-sessions/12"])
    }

    @Test("a discard that fails keeps the workout")
    func discardWorkoutFails() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        server.respond(nil)

        await model.discardWorkout()

        #expect(model.activeSession?.id == 12)
    }

    @Test("sign out forgets the workout in progress")
    func signOutResetsWorkout() async {
        let server = StubServer()
        let model = await workoutModel(server, inProgress: true)
        server.respond { _ in StubResponse(status: 204) }

        await model.signOut()

        #expect(model.activeSession == nil)
    }
}
