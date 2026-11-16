import Foundation
import Testing
@testable import FittCore

@Suite("APIProblem")
struct APIProblemTests {
    private func decode(_ json: String) throws -> APIProblem {
        try JSONDecoder().decode(APIProblem.self, from: Data(json.utf8))
    }

    @Test("reads field errors from properties.errors")
    func readsFieldErrors() throws {
        let problem = try decode("""
        {"type":"about:blank","title":"Bad Request","status":400,"detail":"Validation failed",
         "properties":{"errors":{"email":"Must be a valid email address","password":"Password is required"}}}
        """)
        #expect(problem.status == 400)
        #expect(problem.detail == "Validation failed")
        #expect(problem.fieldErrors == [
            "email": "Must be a valid email address",
            "password": "Password is required",
        ])
    }

    @Test("decodes a problem without properties")
    func decodesWithoutProperties() throws {
        let problem = try decode(#"{"type":"about:blank","title":"Conflict","status":409,"detail":"Email already registered"}"#)
        #expect(problem == APIProblem(status: 409, title: "Conflict", detail: "Email already registered"))
    }

    @Test("decodes a problem with only a status")
    func decodesMinimalBody() throws {
        #expect(try decode(#"{"status":404}"#) == APIProblem(status: 404))
    }
}
