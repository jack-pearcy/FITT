import FittCore
import Foundation

/// Sends one HTTP request to the FITT API and maps the outcome to a `BackendError`.
/// It knows nothing about sessions: the caller decides which token, if any, to send.
struct Transport: Sendable {
    let config: BackendConfig
    let session: URLSession

    /// Returns the body of a 2xx response. Anything else throws a `BackendError`.
    func send(_ method: String, _ path: String, body: Data?, accessToken: String?) async throws -> Data {
        var request = URLRequest(url: config.baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // The server logs this id with every line it writes for the request.
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-ID")

        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
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
        return data
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

/// Trades the stored refresh token for a new pair, at most one exchange at a time.
///
/// The server retires a refresh token the moment it is used, and treats a second
/// use as theft: it revokes the whole login. So when several requests find the
/// access token expired together, exactly one of them may refresh. An actor
/// gives that for free: the rest wait for the same result.
actor TokenRefresher {
    private let transport: Transport
    private let tokenStore: any TokenStore
    private var inFlight: Task<SessionTokens, any Error>?

    init(transport: Transport, tokenStore: any TokenStore) {
        self.transport = transport
        self.tokenStore = tokenStore
    }

    /// Returns tokens newer than `staleAccessToken`, refreshing only if nobody else has.
    func refresh(replacing staleAccessToken: String) async throws -> SessionTokens {
        if let inFlight {
            return try await inFlight.value
        }
        // A caller that queued behind a finished refresh finds the new pair already stored.
        if let current = try? tokenStore.read(), current.accessToken != staleAccessToken {
            return current
        }
        let task = Task { try await exchange() }
        inFlight = task
        defer { inFlight = nil }
        return try await task.value
    }

    private func exchange() async throws -> SessionTokens {
        guard let refreshToken = (try? tokenStore.read())?.refreshToken else {
            throw BackendError.unauthorized
        }
        let body = try JSONEncoder().encode(RefreshRequest(refreshToken: refreshToken))
        let data: Data
        do {
            data = try await transport.send("POST", "/api/identity/auth/refresh", body: body, accessToken: nil)
        } catch BackendError.unauthorized {
            // The server will never accept this token again. Keeping it would only
            // make every later request repeat the same failed exchange.
            try? tokenStore.clear()
            throw BackendError.unauthorized
        }
        guard let response = try? JSONDecoder().decode(TokenResponse.self, from: data) else {
            throw BackendError.undecodableResponse
        }
        let tokens = SessionTokens(response)
        // Stored before anyone uses it: the old refresh token is already dead.
        try tokenStore.save(tokens)
        return tokens
    }
}
