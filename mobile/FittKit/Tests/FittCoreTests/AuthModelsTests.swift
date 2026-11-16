import Foundation
import Testing
@testable import FittCore

@Suite("Auth models")
struct AuthModelsTests {
    @Test("trims whitespace around the email and leaves the password alone")
    func trimsEmail() {
        let credentials = Credentials(email: "  lifter@example.com\n", password: " spaced password ")
        #expect(credentials.email == "lifter@example.com")
        #expect(credentials.password == " spaced password ")
    }

    @Test("encodes the two fields the server expects")
    func encodesCredentials() throws {
        let data = try JSONEncoder().encode(Credentials(email: "a@b.co", password: "pw"))
        let object = try JSONSerialization.jsonObject(with: data) as? [String: String]
        #expect(object == ["email": "a@b.co", "password": "pw"])
    }

    @Test("accepts a password of exactly twelve characters")
    func acceptsMinimum() {
        #expect(PasswordRule.problem(with: String(repeating: "a", count: 12)) == nil)
    }

    @Test("rejects a password under twelve characters")
    func rejectsShort() {
        #expect(PasswordRule.problem(with: String(repeating: "a", count: 11)) != nil)
    }

    @Test("measures the upper limit in bytes, not characters")
    func rejectsLongByBytes() {
        // 72 ASCII characters are 72 bytes. 25 of this character are 75 bytes.
        #expect(PasswordRule.problem(with: String(repeating: "a", count: 72)) == nil)
        #expect(PasswordRule.problem(with: String(repeating: "\u{20AC}", count: 25)) != nil)
    }

    @Test("a token response without a refresh token still decodes")
    func decodesResponseWithoutRefreshToken() throws {
        let response = try JSONDecoder().decode(TokenResponse.self, from: Data(#"{"token":"a.b.c"}"#.utf8))

        #expect(response == TokenResponse(token: "a.b.c", refreshToken: nil))
    }

    @Test("stored tokens round trip")
    func storedTokensRoundTrip() throws {
        let tokens = SessionTokens(accessToken: "a.b.c", refreshToken: "refresh-1")

        #expect(SessionTokens(storedData: try tokens.storedData()) == tokens)
    }

    @Test("a bare access token stored by an older build reads as a session with no refresh token")
    func readsLegacyStoredToken() {
        #expect(SessionTokens(storedData: Data("a.b.c".utf8)) == SessionTokens(accessToken: "a.b.c"))
    }

    @Test("empty stored data is no session")
    func emptyStoredData() {
        #expect(SessionTokens(storedData: Data()) == nil)
    }
}
