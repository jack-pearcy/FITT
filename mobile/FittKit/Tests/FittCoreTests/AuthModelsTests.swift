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
}
