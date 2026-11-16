import FittCore
import Foundation

/// What can go wrong on a call to the FITT API.
public enum BackendError: Error, Equatable, Sendable {
    /// 401. On login the credentials were wrong; anywhere else the token is missing or expired.
    case unauthorized
    /// 429 from the auth rate limiter, with the server's Retry-After in seconds when present.
    case rateLimited(retryAfter: Int?)
    /// Any other 4xx or 5xx that came with a problem body.
    case problem(status: Int, APIProblem)
    /// A status outside 2xx with no readable body.
    case unexpectedStatus(Int)
    /// The response was 2xx but the body did not match the expected type.
    case undecodableResponse
    /// The request never got an HTTP response (offline, timeout, TLS failure).
    case transport
}

/// Every call the app makes to the FITT API goes through this type.
public struct Backend: Sendable {
    private let transport: Transport
    private let tokenStore: any TokenStore
    private let refresher: TokenRefresher
    private let now: @Sendable () -> Date

    public init(
        config: BackendConfig,
        tokenStore: any TokenStore,
        session: URLSession = .shared,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        let transport = Transport(config: config, session: session)
        self.transport = transport
        self.tokenStore = tokenStore
        self.refresher = TokenRefresher(transport: transport, tokenStore: tokenStore)
        self.now = now
    }

    // MARK: Auth

    public func login(_ credentials: Credentials) async throws -> SessionTokens {
        let response: TokenResponse = try await send("POST", "/api/identity/auth/login", body: credentials, authenticated: false)
        return SessionTokens(response)
    }

    public func register(_ credentials: Credentials) async throws -> SessionTokens {
        let response: TokenResponse = try await send("POST", "/api/identity/auth/register", body: credentials, authenticated: false)
        return SessionTokens(response)
    }

    /// Tells the server to end the login this refresh token belongs to.
    public func logout(refreshToken: String) async throws {
        _ = try await perform("POST", "/api/identity/auth/logout", body: RefreshRequest(refreshToken: refreshToken), authenticated: false)
    }

    // MARK: Onboarding

    public func onboardingStatus(userId: Int64) async throws -> OnboardingStatus {
        try await get("/api/users/\(userId)/identity/onboarding/status")
    }

    public func saveBiometrics(_ biometrics: Biometrics, userId: Int64) async throws {
        try await post("/api/users/\(userId)/identity/onboarding/biometrics", body: biometrics)
    }

    /// Maintenance calories, derived by the server from the saved biometrics.
    public func maintenanceCalories(userId: Int64) async throws -> Int {
        let response: Tdee = try await get("/api/users/\(userId)/identity/onboarding/tdee")
        return response.tdee
    }

    // MARK: Goals

    public func saveGoals(_ goals: Goals, userId: Int64) async throws {
        try await post("/api/users/\(userId)/identity/goals", body: goals)
    }

    // MARK: Workouts

    /// What to train next, from the active plan and the sessions finished so far.
    public func nextLift(userId: Int64) async throws -> NextLift {
        try await get("/api/users/\(userId)/next-lift")
    }

    public func workoutPlans(userId: Int64) async throws -> [WorkoutPlan] {
        try await get("/api/users/\(userId)/workout-plans")
    }

    /// Makes this the active plan. The server deactivates the previous one and
    /// returns the plan as it now stands.
    public func activatePlan(_ planId: Int64, userId: Int64) async throws -> WorkoutPlan {
        try await send("POST", "/api/users/\(userId)/workout-plans/\(planId)/activate", body: nil as NoBody?, authenticated: true)
    }

    /// The shared exercise library plus this user's custom exercises.
    public func exercises(userId: Int64) async throws -> [Exercise] {
        try await get("/api/users/\(userId)/exercises")
    }

    /// Creates a plan. The server stores it as not active.
    public func createPlan(_ plan: NewWorkoutPlan, userId: Int64) async throws -> WorkoutPlan {
        try await send("POST", "/api/users/\(userId)/workout-plans", body: plan, authenticated: true)
    }

    // MARK: Transport

    private struct NoBody: Encodable {}

    private func get<Response: Decodable>(_ path: String) async throws -> Response {
        try await send("GET", path, body: nil as NoBody?, authenticated: true)
    }

    /// For the write routes that answer 204 with no body.
    private func post(_ path: String, body: some Encodable) async throws {
        _ = try await perform("POST", path, body: body, authenticated: true)
    }

    private func send<Response: Decodable>(
        _ method: String,
        _ path: String,
        body: (some Encodable)?,
        authenticated: Bool
    ) async throws -> Response {
        let data = try await perform(method, path, body: body, authenticated: authenticated)
        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw BackendError.undecodableResponse
        }
    }

    /// Sends one request and returns the body of a 2xx response. Anything else throws a `BackendError`.
    ///
    /// For an authenticated route this also keeps the session alive: an access
    /// token that has expired is renewed first, and one the server rejects is
    /// renewed and the request sent once more. A 401 means the server did nothing
    /// with the request, so sending it again is safe even for a write.
    private func perform(
        _ method: String,
        _ path: String,
        body: (some Encodable)?,
        authenticated: Bool
    ) async throws -> Data {
        let bodyData = try body.map { try JSONEncoder().encode($0) }
        // The auth routes never get the token: a stale one would be rejected
        // before the credentials are even looked at.
        guard authenticated else {
            return try await transport.send(method, path, body: bodyData, accessToken: nil)
        }

        var tokens = try? tokenStore.read()
        if let current = tokens, current.refreshToken != nil, isExpired(current.accessToken) {
            tokens = try await refresher.refresh(replacing: current.accessToken)
        }
        do {
            return try await transport.send(method, path, body: bodyData, accessToken: tokens?.accessToken)
        } catch BackendError.unauthorized {
            guard let rejected = tokens, rejected.refreshToken != nil else {
                throw BackendError.unauthorized
            }
            let renewed = try await refresher.refresh(replacing: rejected.accessToken)
            return try await transport.send(method, path, body: bodyData, accessToken: renewed.accessToken)
        }
    }

    /// False for a token that cannot be read, so the server gets to judge it.
    private func isExpired(_ accessToken: String) -> Bool {
        guard let claims = try? TokenClaims(token: accessToken) else { return false }
        return claims.isExpired(at: now())
    }
}
