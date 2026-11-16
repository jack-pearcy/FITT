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
    public private(set) var onboardingComplete: Bool?
    public private(set) var isWorking = false
    public private(set) var errorMessage: String?
    /// Per-field messages from a 400, keyed by the request field name.
    public private(set) var fieldErrors: [String: String] = [:]

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
        guard let token = try? tokenStore.read(),
              let claims = try? TokenClaims(token: token),
              !claims.isExpired(at: now())
        else {
            signOut()
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

    public func signOut() {
        try? tokenStore.clear()
        onboardingComplete = nil
        phase = .signedOut
    }

    public func clearErrors() {
        errorMessage = nil
        fieldErrors = [:]
    }

    private func authenticate(_ request: () async throws -> String) async {
        clearErrors()
        isWorking = true
        defer { isWorking = false }
        do {
            let token = try await request()
            let claims = try TokenClaims(token: token)
            try tokenStore.save(token)
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

    private func loadOnboardingStatus(userId: Int64) async {
        do {
            onboardingComplete = try await backend.onboardingStatus(userId: userId).onboardingComplete
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
                // The token was rejected. The session is over; send the user to sign in.
                signOut()
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
