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
    /// The units the user works in. They come from the onboarding status, so they
    /// are right after a relaunch and on a second device. Metric until that loads.
    public private(set) var units = UnitsPreference.metric
    public private(set) var isWorking = false
    public private(set) var errorMessage: String?
    /// Something that went right and is worth saying once, such as "Your account
    /// was deleted." It is not an error and `clearErrors()` leaves it alone:
    /// screens clear errors when they appear, disappear and on every keystroke,
    /// and that would wipe it before it was read. It goes when the next sign in
    /// or registration starts.
    public private(set) var notice: String?
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
    /// Finished workouts, newest first. Nil until loaded.
    public private(set) var history: [WorkoutSession]?
    /// The model the server uses for suggestions. Nil until loaded.
    public private(set) var progressionModel: ProgressionModel?
    /// What to do next for an exercise, by exercise id. An exercise is missing
    /// until its suggestion has loaded, and stays missing if that load fails.
    public private(set) var suggestions: [Int64: ProgressionSuggestion] = [:]
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
        notice = nil
        await authenticate { try await self.backend.login(Credentials(email: email, password: password)) }
    }

    public func register(email: String, password: String) async {
        notice = nil
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

    /// Deletes the account on the server, then forgets the session here. The order
    /// is the reverse of sign out on purpose: the device keeps the session until
    /// the server has confirmed, so the user is never told an account is gone
    /// while it still exists. Returns true when the account was deleted.
    public func deleteAccount(password: String) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        guard !password.isEmpty else {
            fieldErrors = ["password": "Enter your password"]
            return false
        }
        isWorking = true
        defer { isWorking = false }
        var deleted = false
        await load { userId in
            try await self.backend.deleteAccount(AccountDeletion(password: password), userId: userId)
            deleted = true
        }
        guard deleted else { return false }
        endSession()
        notice = "Your account was deleted."
        return true
    }

    /// Changes the password and keeps this device signed in with the pair the
    /// server hands back. Every other device is signed out by the server.
    /// Returns true when the password was changed and the new session is stored.
    public func changePassword(current: String, new: String) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        var problems: [String: String] = [:]
        if current.isEmpty {
            problems["currentPassword"] = "Enter your current password"
        }
        if let problem = PasswordRule.problem(with: new) {
            problems["newPassword"] = problem
        } else if new == current {
            problems["newPassword"] = "New password must be different from the current one"
        }
        guard problems.isEmpty else {
            fieldErrors = problems
            return false
        }
        isWorking = true
        defer { isWorking = false }
        var issued: SessionTokens?
        await load { userId in
            issued = try await self.backend.changePassword(
                PasswordChange(currentPassword: current, newPassword: new), userId: userId)
        }
        guard let issued else { return false }
        do {
            try tokenStore.save(issued)
        } catch {
            // The password did change, and the tokens this device held died with
            // the old one. Without the new pair there is no session to continue.
            endSession()
            errorMessage = "Password changed, but this device could not save the session. Sign in again."
            return false
        }
        return true
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
        history = nil
        progressionModel = nil
        suggestions = [:]
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

    /// Replaces a saved plan with `draft`. Returns true when it was saved, so the
    /// form knows to close. Today is reloaded too: the edit can change the day
    /// that comes next.
    public func updatePlan(_ planId: Int64, with draft: PlanDraft) async -> Bool {
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
        var saved = false
        await load { userId in
            try await self.backend.updatePlan(request, planId: planId, userId: userId)
            saved = true
        }
        guard saved else { return false }
        await loadPlans()
        await loadNextLift()
        return true
    }

    /// Deletes a plan. Returns true when it is gone, so the detail screen knows
    /// to close. Deleting the active plan leaves no plan active.
    public func deletePlan(_ planId: Int64) async -> Bool {
        guard case .signedIn = phase else { return false }
        clearErrors()
        isWorking = true
        defer { isWorking = false }
        var deleted = false
        await load { userId in
            try await self.backend.deletePlan(planId, userId: userId)
            deleted = true
        }
        guard deleted else { return false }
        await loadPlans()
        await loadNextLift()
        return true
    }

    public func loadExercises() async {
        await load { userId in self.exercises = try await self.backend.exercises(userId: userId) }
    }

    /// Creates the custom exercise in `draft` and returns it, or nil when it was
    /// not saved. The server's answer is the stored exercise, so it is added to
    /// the loaded library as it is, with no second request.
    public func createExercise(_ draft: ExerciseDraft) async -> Exercise? {
        guard case .signedIn = phase else { return nil }
        clearErrors()
        let request: NewExercise
        do {
            request = try draft.exercise()
        } catch {
            fieldErrors = error.fields
            return nil
        }

        isWorking = true
        defer { isWorking = false }
        var created: Exercise?
        await load { userId in
            created = try await self.backend.createExercise(request, userId: userId)
        }
        if let created {
            exercises?.append(created)
        }
        return created
    }

    /// Deletes a custom exercise and takes it out of the loaded library. The
    /// server refuses one that a plan or a logged workout uses, and its
    /// sentence saying so becomes the error message.
    public func deleteExercise(_ exerciseId: Int64) async {
        await load { userId in
            try await self.backend.deleteExercise(exerciseId, userId: userId)
            self.exercises?.removeAll { $0.id == exerciseId }
        }
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
            entry = try draft.entry(today: CalendarDate(now()), units: units)
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
        await loadSessions()
    }

    /// Reads every session once and splits it: the unfinished one is the workout
    /// in progress and the finished ones are the history. The API has one list
    /// route for both, so one request serves the Today tab and the History tab.
    public func loadSessions() async {
        await load { userId in
            let sessions = try await self.backend.sessions(userId: userId)
            self.activeSession = sessions.first { !$0.isFinished }
            self.history = SessionHistory.finished(sessions)
        }
    }

    /// Deletes a finished workout. Returns true when it is gone, so the screen
    /// showing it knows to close. The plan's next day is read again because the
    /// server works it out from the finished sessions.
    public func deleteFromHistory(_ sessionId: Int64) async -> Bool {
        guard case .signedIn = phase, !isWorking else { return false }
        isWorking = true
        defer { isWorking = false }
        var deleted = false
        await load { userId in
            try await self.backend.deleteSession(sessionId, userId: userId)
            deleted = true
        }
        guard deleted else { return false }
        history?.removeAll { $0.id == sessionId }
        // Suggestions are worked out from the finished sessions.
        suggestions = [:]
        await loadNextLift()
        return true
    }

    // MARK: Progression

    public func loadProgressionModel() async {
        await load { userId in
            self.progressionModel = try await self.backend.progressionModel(userId: userId)
        }
    }

    /// Changes the model, then reads it back so the screen shows what the
    /// server stored. Suggestions made by the old model are dropped.
    public func setProgressionModel(_ model: ProgressionModel) async {
        guard case .signedIn = phase, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        await load { userId in
            try await self.backend.setProgressionModel(model, userId: userId)
            self.suggestions = [:]
            self.progressionModel = try await self.backend.progressionModel(userId: userId)
        }
    }

    /// Loads a suggestion for every exercise of the workout in progress that
    /// does not have one yet. A suggestion is help, not a step of the workout,
    /// so a failure here is silent: the exercise is shown without one, and no
    /// error message replaces what the user is doing.
    public func loadSuggestions() async {
        guard case .signedIn(let userId) = phase, let session = activeSession else { return }
        for entry in session.orderedEntries where suggestions[entry.exercise.id] == nil {
            guard let suggestion = try? await backend.suggestion(exerciseId: entry.exercise.id, userId: userId) else { continue }
            // The answer is dropped if the user signed out while it was on its way.
            guard case .signedIn(userId) = phase else { return }
            suggestions[entry.exercise.id] = suggestion
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

    /// Adds an exercise to the workout in progress, after the ones already in
    /// it. The plan is not changed: this is for today only.
    public func addExercise(_ exercise: Exercise) async {
        guard let session = activeSession, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        await load { userId in
            self.activeSession = try await self.backend.addEntry(exerciseId: exercise.id, sessionId: session.id, userId: userId)
        }
    }

    /// Removes an exercise from the workout in progress. The server deletes the
    /// sets logged for it too.
    public func removeExercise(entryId: Int64) async {
        guard let session = activeSession, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        await load { userId in
            self.activeSession = try await self.backend.removeEntry(entryId, sessionId: session.id, userId: userId)
        }
    }

    /// Logs one set. Returns true when it was saved.
    public func logSet(_ draft: SetDraft, entryId: Int64) async -> Bool {
        // A second tap while the first is on its way would log the set twice.
        guard let session = activeSession, !isWorking else { return false }
        clearErrors()
        let set: LoggedSet
        do {
            set = try draft.loggedSet(units: units)
        } catch {
            fieldErrors = Self.keyed(error.fields, entryId: entryId)
            return false
        }
        isWorking = true
        defer { isWorking = false }
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

    /// Saves the notes of the workout in progress. Returns true when the server
    /// has them, or when the text is what is already stored, in which case
    /// nothing is sent.
    public func saveNotes(_ text: String) async -> Bool {
        guard let session = activeSession else { return false }
        let notes: SessionNotes
        do {
            notes = try SessionNotes(text: text)
        } catch {
            fieldErrors = error.fields
            return false
        }
        guard notes.notes != session.notes else { return true }
        var saved = false
        await load { userId in
            let updated = try await self.backend.updateNotes(notes, sessionId: session.id, userId: userId)
            // The user may have discarded the workout while the request was out.
            if self.activeSession?.id == updated.id {
                self.activeSession = updated
            }
            saved = true
        }
        return saved
    }

    /// Finishes the workout. A finished session is what moves the plan to its next day.
    public func finishWorkout() async {
        guard let session = activeSession, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        var finished: WorkoutSession?
        await load { userId in
            finished = try await self.backend.finishSession(session.id, userId: userId)
        }
        guard let finished else { return }
        activeSession = nil
        // The next suggestion depends on the workout that was just finished.
        suggestions = [:]
        // The server's answer is the finished session, so it goes into a loaded
        // history as it is.
        if let history {
            self.history = SessionHistory.finished(history.filter { $0.id != finished.id } + [finished])
        }
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
            let status = try await backend.onboardingStatus(userId: userId)
            onboarding = status
            if let saved = status.unitsPreference {
                units = saved
            }
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
