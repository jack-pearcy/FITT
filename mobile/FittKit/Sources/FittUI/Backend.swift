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
    private let config: BackendConfig
    private let tokenStore: any TokenStore
    private let session: URLSession

    public init(config: BackendConfig, tokenStore: any TokenStore, session: URLSession = .shared) {
        self.config = config
        self.tokenStore = tokenStore
        self.session = session
    }

    // MARK: Auth

    public func login(_ credentials: Credentials) async throws -> String {
        let response: TokenResponse = try await send("POST", "/api/identity/auth/login", body: credentials, authenticated: false)
        return response.token
    }

    public func register(_ credentials: Credentials) async throws -> String {
        let response: TokenResponse = try await send("POST", "/api/identity/auth/register", body: credentials, authenticated: false)
        return response.token
    }

    // MARK: Onboarding

    public func onboardingStatus(userId: Int64) async throws -> OnboardingStatus {
        try await send("GET", "/api/users/\(userId)/identity/onboarding/status")
    }

    // MARK: Transport

    private struct NoBody: Encodable {}

    private func send<Response: Decodable>(_ method: String, _ path: String) async throws -> Response {
        try await send(method, path, body: nil as NoBody?, authenticated: true)
    }

    private func send<Response: Decodable>(
        _ method: String,
        _ path: String,
        body: (some Encodable)?,
        authenticated: Bool
    ) async throws -> Response {
        var request = URLRequest(url: config.baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // The server logs this id with every line it writes for the request.
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-ID")

        if let body {
            request.httpBody = try JSONEncoder().encode(body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        // The auth routes never get the token: a stale one would be rejected
        // before the credentials are even looked at.
        if authenticated, let token = try? tokenStore.read() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw BackendError.transport
        }
        guard let http = response as? HTTPURLResponse else { throw BackendError.transport }

        guard (200..<300).contains(http.statusCode) else {
            throw Self.error(status: http.statusCode, headers: http, body: data)
        }
        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw BackendError.undecodableResponse
        }
    }

    private static func error(status: Int, headers: HTTPURLResponse, body: Data) -> BackendError {
        switch status {
        case 401:
            return .unauthorized
        case 429:
            return .rateLimited(retryAfter: headers.value(forHTTPHeaderField: "Retry-After").flatMap(Int.init))
        default:
            if let problem = try? JSONDecoder().decode(APIProblem.self, from: body) {
                return .problem(status: status, problem)
            }
            return .unexpectedStatus(status)
        }
    }
}
