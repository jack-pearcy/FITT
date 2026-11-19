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

/// The response of login, register, and refresh.
public struct TokenResponse: Decodable, Equatable, Sendable {
    public let token: String
    /// Absent from a server that predates refresh tokens.
    public let refreshToken: String?

    public init(token: String, refreshToken: String? = nil) {
        self.token = token
        self.refreshToken = refreshToken
    }
}

/// The body of `POST /api/identity/auth/refresh` and `/logout`.
public struct RefreshRequest: Encodable, Equatable, Sendable {
    public let refreshToken: String

    public init(refreshToken: String) {
        self.refreshToken = refreshToken
    }
}

/// The body of `POST /api/users/{id}/identity/account/delete`: the current
/// password, typed again, so a session alone is not enough to delete an account.
public struct AccountDeletion: Encodable, Equatable, Sendable {
    public let password: String

    public init(password: String) {
        self.password = password
    }
}

/// What the app keeps for a signed-in user: the access token sent with every
/// request, and the refresh token that buys the next pair when it expires.
public struct SessionTokens: Codable, Equatable, Sendable {
    public let accessToken: String
    /// Nil when the server issued none. The session then ends when the access token does.
    public let refreshToken: String?

    public init(accessToken: String, refreshToken: String? = nil) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }

    public init(_ response: TokenResponse) {
        self.init(accessToken: response.token, refreshToken: response.refreshToken)
    }

    /// The bytes written to the Keychain.
    public func storedData() throws -> Data {
        try JSONEncoder().encode(self)
    }

    /// Reads what `storedData()` wrote. A build from before refresh tokens stored
    /// the bare access token, so anything that is not this JSON is read as that.
    public init?(storedData: Data) {
        if let tokens = try? JSONDecoder().decode(SessionTokens.self, from: storedData) {
            self = tokens
        } else if let legacy = String(data: storedData, encoding: .utf8), !legacy.isEmpty {
            self.init(accessToken: legacy)
        } else {
            return nil
        }
    }
}

/// Mirrors the backend `OnboardingStatusResponse` record.
public struct OnboardingStatus: Decodable, Equatable, Sendable {
    public let biometricsComplete: Bool
    public let goalsComplete: Bool
    public let onboardingComplete: Bool
    /// The units saved with the biometrics. Nil before they are saved, and from
    /// a server that does not send the field yet.
    public let unitsPreference: UnitsPreference?

    public init(biometricsComplete: Bool, goalsComplete: Bool, onboardingComplete: Bool, unitsPreference: UnitsPreference? = nil) {
        self.biometricsComplete = biometricsComplete
        self.goalsComplete = goalsComplete
        self.onboardingComplete = onboardingComplete
        self.unitsPreference = unitsPreference
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
