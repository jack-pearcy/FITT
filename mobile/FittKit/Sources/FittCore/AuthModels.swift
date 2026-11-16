import Foundation

/// The body of both `POST /api/identity/auth/login` and `/register`.
public struct Credentials: Encodable, Equatable, Sendable {
    public let email: String
    public let password: String

    /// Surrounding whitespace is dropped from the email, since a pasted or
    /// autofilled address often carries a trailing space. The password is sent as typed.
    public init(email: String, password: String) {
        self.email = email.trimmingCharacters(in: .whitespacesAndNewlines)
        self.password = password
    }
}

/// The response of login and register: the access token and nothing else.
public struct TokenResponse: Decodable, Equatable, Sendable {
    public let token: String

    public init(token: String) {
        self.token = token
    }
}

/// Mirrors the backend `OnboardingStatusResponse` record.
public struct OnboardingStatus: Decodable, Equatable, Sendable {
    public let biometricsComplete: Bool
    public let goalsComplete: Bool
    public let onboardingComplete: Bool

    public init(biometricsComplete: Bool, goalsComplete: Bool, onboardingComplete: Bool) {
        self.biometricsComplete = biometricsComplete
        self.goalsComplete = goalsComplete
        self.onboardingComplete = onboardingComplete
    }
}

/// The register rules the server enforces, checked on the device first so the
/// user sees the problem before a round trip. The server stays the authority.
public enum PasswordRule {
    public static let minimumCharacters = 12
    /// BCrypt reads only the first 72 bytes, so the server rejects anything longer.
    public static let maximumBytes = 72

    /// Returns the message to show, or nil when the password is acceptable.
    public static func problem(with password: String) -> String? {
        if password.count < minimumCharacters {
            return "Password must be at least \(minimumCharacters) characters"
        }
        if password.utf8.count > maximumBytes {
            return "Password is too long"
        }
        return nil
    }
}
