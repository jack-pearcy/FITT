import Foundation

/// The claims the app reads from a FITT access token.
///
/// The login response carries only the token string. The user id that every
/// `/api/users/{userId}/...` route needs exists only as the `uid` claim inside it.
/// The signature is not checked here: the server verifies it on every request,
/// and the app holds no signing key.
public struct TokenClaims: Equatable, Sendable {
    public let userId: Int64
    public let expiresAt: Date

    public init(userId: Int64, expiresAt: Date) {
        self.userId = userId
        self.expiresAt = expiresAt
    }

    public enum DecodingError: Error, Equatable {
        /// Not three dot-separated segments.
        case malformedToken
        /// The payload segment is not valid base64url.
        case malformedPayload
        /// The payload is not JSON, or lacks `uid` or `exp`.
        case missingClaims
    }

    public init(token: String) throws(DecodingError) {
        let segments = token.split(separator: ".", omittingEmptySubsequences: false)
        guard segments.count == 3 else { throw .malformedToken }
        guard let payload = Self.base64URLDecode(String(segments[1])) else { throw .malformedPayload }
        guard let claims = try? JSONDecoder().decode(Payload.self, from: payload) else { throw .missingClaims }
        self.userId = claims.uid
        self.expiresAt = Date(timeIntervalSince1970: claims.exp)
    }

    /// True once the token is past its expiry, with a margin so a request
    /// started just before expiry is not sent with a token the server rejects.
    public func isExpired(at now: Date, margin: TimeInterval = 30) -> Bool {
        now.addingTimeInterval(margin) >= expiresAt
    }

    private struct Payload: Decodable {
        let uid: Int64
        let exp: TimeInterval
    }

    /// JWT segments use the URL-safe alphabet and drop the `=` padding (RFC 7515 section 2),
    /// while Foundation's decoder wants the standard alphabet with padding.
    private static func base64URLDecode(_ segment: String) -> Data? {
        var base64 = segment
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder == 1 { return nil }
        if remainder > 0 { base64 += String(repeating: "=", count: 4 - remainder) }
        return Data(base64Encoded: base64)
    }
}
