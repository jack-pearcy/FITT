import Foundation
import Testing
@testable import FittCore

@Suite("TokenClaims")
struct TokenClaimsTests {
    /// Builds an unsigned token the way the server shapes one: header.payload.signature,
    /// each segment base64url without padding.
    private func token(payload: String) -> String {
        func segment(_ text: String) -> String {
            Data(text.utf8).base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
        }
        return [segment(#"{"alg":"HS256"}"#), segment(payload), "signature"].joined(separator: ".")
    }

    @Test("reads uid and exp from the payload")
    func readsClaims() throws {
        let claims = try TokenClaims(token: token(payload: #"{"iss":"fitt-api","uid":10,"exp":1800000000}"#))
        #expect(claims.userId == 10)
        #expect(claims.expiresAt == Date(timeIntervalSince1970: 1_800_000_000))
    }

    // Payload lengths that leave 0, 2 and 3 characters in the last base64 group.
    @Test("decodes payloads whose base64url form needs padding restored", arguments: [
        #"{"uid":1,"exp":1}"#,
        #"{"uid":12,"exp":1}"#,
        #"{"uid":123,"exp":1}"#,
    ])
    func restoresPadding(payload: String) throws {
        #expect(throws: Never.self) { try TokenClaims(token: token(payload: payload)) }
    }

    @Test("decodes a payload that uses the URL-safe alphabet")
    func urlSafeAlphabet() throws {
        // The "?>" and "??" runs encode to "/" and "+" in standard base64.
        let source = token(payload: #"{"uid":7,"exp":1,"sub":"a?>b??c"}"#)
        #expect(source.contains("_") || source.contains("-"))
        #expect(try TokenClaims(token: source).userId == 7)
    }

    @Test("rejects a token that is not three segments")
    func rejectsWrongShape() {
        #expect(throws: TokenClaims.DecodingError.malformedToken) { try TokenClaims(token: "only.two") }
    }

    @Test("rejects a payload that is not base64url")
    func rejectsBadBase64() {
        #expect(throws: TokenClaims.DecodingError.malformedPayload) { try TokenClaims(token: "a.!!!!.c") }
    }

    @Test("rejects a payload without uid")
    func rejectsMissingClaims() {
        #expect(throws: TokenClaims.DecodingError.missingClaims) {
            try TokenClaims(token: token(payload: #"{"exp":1800000000}"#))
        }
    }

    @Test("treats a token inside the margin as expired")
    func expiryMargin() {
        let claims = TokenClaims(userId: 1, expiresAt: Date(timeIntervalSince1970: 1000))
        #expect(!claims.isExpired(at: Date(timeIntervalSince1970: 900)))
        #expect(claims.isExpired(at: Date(timeIntervalSince1970: 980)))
        #expect(claims.isExpired(at: Date(timeIntervalSince1970: 1000)))
    }
}
