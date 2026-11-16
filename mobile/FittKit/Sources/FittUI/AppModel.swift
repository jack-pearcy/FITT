import FittCore
import Foundation
import Observation

/// The single source of truth for app state. Screens read it and call its methods.
@MainActor
@Observable
public final class AppModel {
    public enum Phase: Equatable, Sendable {
        case loading
        case signedOut
        case signedIn(userId: Int64)
    }

    public private(set) var phase: Phase = .loading
    /// Nil until the server has answered, or when it could not be reached.
    public private(set) var onboarding: OnboardingStatus?
    /// Maintenance calories per day, once biometrics are saved and the server has been asked.
    public private(set) var maintenanceCalories: Int?
    /// The units chosen on the biometrics step. The API has no route to read them
    /// back, so after a relaunch this falls back to metric.
    public private(set) var units = UnitsPreference.metric
    public private(set) var isWorking = false
    public private(set) var errorMessage: String?
    /// Per-field messages from a 400, keyed by the request field name.
    public private(set) var fieldErrors: [String: String] = [:]

    /// What to train next. Nil until loaded.
    public private(set) var nextLift: NextLift?
    /// The user's plans. Nil until loaded, empty when there are none.
    public private(set) var plans: [WorkoutPlan]?

    /// The saved goals. Nil until loaded.
    public private(set) var goals: Goals?
    /// Body weight entries, newest first. Nil until loaded.
    public private(set) var weightEntries: [BodyWeightEntry]?
    /// Average of the last seven days. Nil until loaded, or when nothing was logged in them.
    public private(set) var weightAverage: Decimal?
    /// The workout in progress, if there is one.
    public private(set) var activeSession: WorkoutSession?
    /// The exercise library. Nil until loaded.
    public private(set) var exercises: [Exercise]?

    public var onboardingComplete: Bool? { onboarding?.onboardingComplete }

    private let backend: Backend
    private let tokenStore: any TokenStore
    private let now: @Sendable () -> Date

    public init(backend: Backend, tokenStore: any TokenStore, now: @escaping @Sendable () -> Date = { Date() }) {
        self.backend = backend
        self.tokenStore = tokenStore
        self.now = now
    }

    /// Restores the session from the Keychain on launch.
    public func start() async {
        // An expired access token is fine while a refresh token is stored:
        // the backend client renews it on the first request.
        guard let tokens = try? tokenStore.read(),
              let claims = try? TokenClaims(token: tokens.accessToken),
              tokens.refreshToken != nil || !claims.isExpired(at: now())
        else {
            endSession()
            return
        }
        phase = .signedIn(userId: claims.userId)
        await loadOnboardingStatus(userId: claims.userId)
    }

    public func signIn(email: String, password: String) async {
        await authenticate { try await self.backend.login(Credentials(email: email, password: password)) }
    }

    public func register(email: String, password: String) async {
        if let problem = PasswordRule.problem(with: password) {
            errorMessage = nil
            fieldErrors = ["password": problem]
            return
        }
        await authenticate { try await self.backend.register(Credentials(email: email, password: password)) }
    }

    /// The user chose to sign out. The device forgets the session first, so the
    /// screen changes at once, then the server is told to end the login. That
    /// call is best effort: offline, the refresh token simply expires unused.
    public func signOut() async {
        let refreshToken = (try? tokenStore.read())?.refreshToken
        endSession()
        if let refreshToken {
            try? await backend.logout(refreshToken: refreshToken)
        }
    }

    /// Forgets the session on this device and shows the sign-in screen.
    private func endSession() {
        try? tokenStore.clear()
        onboarding = nil
        maintenanceCalories = nil
        nextLift = nil
        plans = nil
        exercises = nil
        activeSession = nil
        weightEntries = nil
        weightAverage = nil
        goals = nil
        units = .metric
        phase = .signedOut
    }

    // MARK: Onboarding

    /// Asks the server again which onboarding steps are done, for a retry after a failed load.
    public func refreshOnboarding() async {
        guard case .signedIn(let userId) = phase else { return }
        clearErrors()
        isWorking = true
        defer { isWorking = false }
        await loadOnboardingStatus(userId: userId)
    }

    public func saveBiometrics(_ draft: BiometricsDraft) async {
        guard case .signedIn(let userId) = phase else { return }
        clearErrors()
        let biometrics: Biometrics
        do {
            biometrics = try draft.biometrics(today: CalendarDate(now()))
        } catch {
            fieldErrors = error.fields
            return
        }
        await save(userId: userId) {
            try await self.backend.saveBiometrics(biometrics, userId: userId)
            self.units = biometrics.unitsPreference
        }
    }

    public func saveGoals(_ draft: GoalsDraft) async {
        guard case .signedIn(let userId) = phase else { return }
        clearErrors()
        let goals: Goals
        do {
            goals = try draft.goals(units: units)
        } catch {
            fieldErrors = error.fields
            return
        }
        await save(userId: userId) {
            try await self.backend.saveGoals(goals, userId: userId)
        }
    }

    /// Loads maintenance calories for the goals step. The step works without the
    /// number, so a failure here is shown but does not block it.
    public func loadMaintenanceCalories() async {
        guard case .signedIn(let userId) = phase else { return }
        do {
            maintenanceCalories = try await backend.maintenanceCalories(userId: userId)
        } catch let error as BackendError {
            show(error, duringSignIn: false)
        } catch {
            errorMessage = "Something went wrong. Try again."
        }
    }

    // MARK: Workouts

    public func loadNextLift() async {
        await load { userId in self.nextLift = try await self.backend.nextLift(userId: userId) }
    }

    public func loadPlans() async {
        await load { userId in self.plans = try await self.backend.workoutPlans(userId: userId) }
    }

    /// Switches the active plan, then reloads both screens that depend on it:
    /// the server deactivates the old plan and changes what comes next.
    public func activatePlan(_ planId: Int64) async {
        guard case .signedIn = phase else { return }
        isWorking = true
        defer { isWorking = false }
        await load { userId in _ = try await self.backend.activatePlan(planId, userId: userId) }
        guard errorMessage == nil else { return }
        await loadPlans()
        await loadNextLift()
    }

    public func loadExercises() async {
        await load { userId in self.exercises = try await self.backend.exercises(userId: userId) }
    }

    /// Creates the plan in `draft`. Returns true when it was saved, so the form
    /// knows to close. A plan becomes the active one when no other plan is:
    /// a first plan should show up under Today without a second step.
    public func createPlan(_ draft: PlanDraft) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        let request: NewWorkoutPlan
        do {
            request = try draft.plan()
        } catch {
            fieldErrors = error.fields
            return false
        }

        isWorking = true
        defer { isWorking = false }
        var created: WorkoutPlan?
        await load { userId in
            let plan = try await self.backend.createPlan(request, userId: userId)
            created = plan
            let others = try await self.backend.workoutPlans(userId: userId)
            if !others.contains(where: \.active) {
                _ = try await self.backend.activatePlan(plan.id, userId: userId)
            }
        }
        guard created != nil else { return false }
        // The plan exists even if activating it failed, so the lists are
        // reloaded either way and the form closes.
        let activationProblem = errorMessage
        await loadPlans()
        await loadNextLift()
        errorMessage = errorMessage ?? activationProblem
        return true
    }

    // MARK: Goals after onboarding

    public func loadGoals() async {
        await load { userId in self.goals = try await self.backend.goals(userId: userId) }
    }

    /// Saves changed goals, then reads them back: in automatic mode the server
    /// recalculates the calorie goal, so the saved value is not the one sent.
    /// Returns true when the save went through, so the form knows to close.
    public func updateGoals(_ draft: GoalsDraft) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        let changed: Goals
        do {
            changed = try draft.goals(units: units)
        } catch {
            fieldErrors = error.fields
            return false
        }
        isWorking = true
        defer { isWorking = false }
        var saved = false
        await load { userId in
            try await self.backend.saveGoals(changed, userId: userId)
            saved = true
        }
        guard saved else { return false }
        await loadGoals()
        return true
    }

    // MARK: Body weight

    /// How far back the weight log reads.
    public static let weightLogDays = 90

    public func loadWeightLog() async {
        await load { userId in
            let entries = try await self.backend.bodyWeights(userId: userId, days: Self.weightLogDays)
            let average = try await self.backend.bodyWeightAverage(userId: userId)
            self.weightEntries = entries
            // The server answers zero, not null, when the last seven days are empty.
            self.weightAverage = average == 0 ? nil : average
        }
    }

    /// Logs the weight in `draft`. Returns true when it was saved, so the form knows to close.
    public func logWeight(_ draft: BodyWeightDraft) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        let entry: NewBodyWeight
        do {
            entry = try draft.entry(today: CalendarDate(now()))
        } catch {
            fieldErrors = error.fields
            return false
        }
        isWorking = true
        defer { isWorking = false }
        var saved = false
        await load { userId in
            _ = try await self.backend.logBodyWeight(entry, userId: userId)
            saved = true
        }
        guard saved else { return false }
        await loadWeightLog()
        return true
    }

    public func deleteWeight(_ entryId: Int64) async {
        var deleted = false
        await load { userId in
            try await self.backend.deleteBodyWeight(entryId, userId: userId)
            deleted = true
        }
        guard deleted else { return }
        await loadWeightLog()
    }

    // MARK: Workout in progress

    /// Field-error key for one entry's set form, so a message lands under the
    /// right exercise when several are on screen.
    public static func setFieldKey(entryId: Int64, field: String) -> String {
        "entry.\(entryId).\(field)"
    }

    /// Finds a session left unfinished, such as one the app was closed in the middle of.
    public func loadActiveSession() async {
        await load { userId in
            let sessions = try await self.backend.sessions(userId: userId)
            self.activeSession = sessions.first { !$0.isFinished }
        }
    }

    /// Starts a session for the day the server says is next, then adds that
    /// day's exercises to it. The session is stored after every step, so if an
    /// exercise fails to add, what was created is still shown.
    public func startWorkout() async {
        guard case .signedIn = phase, !isWorking, activeSession == nil,
              let next = nextLift, let day = next.day
        else { return }
        isWorking = true
        defer { isWorking = false }
        let startedAt = now()
        await load { userId in
            var session = try await self.backend.startSession(
                NewWorkoutSession(startedAt: startedAt, planId: next.planId), userId: userId)
            self.activeSession = session
            for item in day.orderedExercises {
                session = try await self.backend.addEntry(exerciseId: item.exercise.id, sessionId: session.id, userId: userId)
                self.activeSession = session
            }
        }
    }

    /// Logs one set. Returns true when it was saved.
    public func logSet(_ draft: SetDraft, entryId: Int64) async -> Bool {
        guard let session = activeSession else { return false }
        clearErrors()
        let set: LoggedSet
        do {
            set = try draft.loggedSet()
        } catch {
            fieldErrors = Self.keyed(error.fields, entryId: entryId)
            return false
        }
        var saved = false
        await load { userId in
            self.activeSession = try await self.backend.logSet(set, entryId: entryId, sessionId: session.id, userId: userId)
            saved = true
        }
        // A 400 from the server names the same fields; file them under this entry too.
        fieldErrors = Self.keyed(fieldErrors, entryId: entryId)
        return saved
    }

    public func deleteSet(_ setId: Int64, entryId: Int64) async {
        guard let session = activeSession else { return }
        await load { userId in
            self.activeSession = try await self.backend.deleteSet(setId, entryId: entryId, sessionId: session.id, userId: userId)
        }
    }

    /// Finishes the workout. A finished session is what moves the plan to its next day.
    public func finishWorkout() async {
        guard let session = activeSession, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        var finished = false
        await load { userId in
            _ = try await self.backend.finishSession(session.id, userId: userId)
            finished = true
        }
        guard finished else { return }
        activeSession = nil
        await loadNextLift()
    }

    /// Deletes the workout in progress and everything logged in it.
    public func discardWorkout() async {
        guard let session = activeSession, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        await load { userId in
            try await self.backend.deleteSession(session.id, userId: userId)
            self.activeSession = nil
        }
    }

    private static func keyed(_ fields: [String: String], entryId: Int64) -> [String: String] {
        Dictionary(uniqueKeysWithValues: fields.map { (setFieldKey(entryId: entryId, field: $0.key), $0.value) })
    }

    /// Runs one read or write for the signed-in user and shows what went wrong, if anything.
    private func load(_ work: (Int64) async throws -> Void) async {
        guard case .signedIn(let userId) = phase else { return }
        errorMessage = nil
        do {
            try await work(userId)
        } catch let error as BackendError {
            show(error, duringSignIn: false)
        } catch {
            errorMessage = "Something went wrong. Try again."
        }
    }

    public func clearErrors() {
        errorMessage = nil
        fieldErrors = [:]
    }

    private func authenticate(_ request: () async throws -> SessionTokens) async {
        clearErrors()
        isWorking = true
        defer { isWorking = false }
        do {
            let tokens = try await request()
            let claims = try TokenClaims(token: tokens.accessToken)
            try tokenStore.save(tokens)
            phase = .signedIn(userId: claims.userId)
            await loadOnboardingStatus(userId: claims.userId)
        } catch let error as BackendError {
            show(error, duringSignIn: true)
        } catch is KeychainError {
            // The server accepted the credentials but the token could not be stored,
            // so there is no session to continue with.
            errorMessage = "Signed in, but this device could not save the session."
        } catch {
            errorMessage = "Something went wrong. Try again."
        }
    }

    /// Runs one onboarding write, then reads the status back so the server
    /// decides which step comes next.
    private func save(userId: Int64, _ write: () async throws -> Void) async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await write()
        } catch let error as BackendError {
            show(error, duringSignIn: false)
            return
        } catch {
            errorMessage = "Something went wrong. Try again."
            return
        }
        await loadOnboardingStatus(userId: userId)
    }

    private func loadOnboardingStatus(userId: Int64) async {
        do {
            onboarding = try await backend.onboardingStatus(userId: userId)
        } catch let error as BackendError {
            show(error, duringSignIn: false)
        } catch {
            errorMessage = "Something went wrong. Try again."
        }
    }

    private func show(_ error: BackendError, duringSignIn: Bool) {
        switch error {
        case .unauthorized:
            if duringSignIn {
                errorMessage = "Email or password is incorrect."
            } else {
                // The token was rejected and could not be renewed. The session is
                // over; send the user to sign in.
                endSession()
                errorMessage = "Your session ended. Sign in again."
            }
        case .rateLimited(let retryAfter):
            if let retryAfter {
                errorMessage = "Too many attempts. Try again in \(retryAfter) seconds."
            } else {
                errorMessage = "Too many attempts. Try again in a minute."
            }
        case .problem(_, let problem):
            fieldErrors = problem.fieldErrors
            errorMessage = problem.fieldErrors.isEmpty ? (problem.detail ?? "The request was rejected.") : nil
        case .transport:
            errorMessage = "Cannot reach the server. Check the connection."
        case .unexpectedStatus, .undecodableResponse:
            errorMessage = "The server gave an unexpected answer. Try again."
        }
    }
}
