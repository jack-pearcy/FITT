import FittCore
import Foundation
import Testing
@testable import FittAPI
@testable import FittUI

@Suite("Connecting a second device")
struct DeviceLinkTests {
    private let codeBody = #"{"code":"one-time-code","expiresInSeconds":60}"#

    private func pair(_ access: String, _ refresh: String) -> String {
        #"{"token":"\#(access)","refreshToken":"\#(refresh)"}"#
    }

    private func body(_ request: RecordedRequest) throws -> [String: String]? {
        try JSONSerialization.jsonObject(with: request.body) as? [String: String]
    }

    // MARK: The phone asks for a code

    @Test("asking for a code sends both tokens to the signed-in user's route")
    func codeRequestCarriesBothTokens() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }
        let access = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600))
        let store = InMemoryTokenStore(token: access, refreshToken: "refresh-1")

        let code = try await server.backend(tokenStore: store).deviceLinkCode()

        #expect(code == DeviceLinkCode(code: "one-time-code", expiresInSeconds: 60))
        #expect(server.requests.count == 1)
        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/users/10/identity/device-link")
        #expect(request.headers["Authorization"] == "Bearer \(access)")
        #expect(try body(request) == ["refreshToken": "refresh-1"])
    }

    @Test("an expired access token is renewed first, and the new refresh token is the one sent")
    func renewsBeforeAsking() async throws {
        let server = StubServer()
        let expired = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(-60))
        let fresh = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600))
        let renewed = pair(fresh, "refresh-2")
        server.respond { request in
            request.path.hasSuffix("/refresh") ? .json(200, renewed) : .json(200, codeBody)
        }
        let store = InMemoryTokenStore(token: expired, refreshToken: "refresh-1")

        _ = try await server.backend(tokenStore: store).deviceLinkCode()

        #expect(server.requests.map(\.path) == ["/api/identity/auth/refresh", "/api/users/10/identity/device-link"])
        let request = try #require(server.requests.last)
        #expect(request.headers["Authorization"] == "Bearer \(fresh)")
        #expect(try body(request) == ["refreshToken": "refresh-2"])
    }

    @Test("a 401 is answered with one refresh and one more try with the new pair")
    func retriesOnceAfterRefusal() async throws {
        let server = StubServer()
        let first = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600))
        let second = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(7200))
        let renewed = pair(second, "refresh-2")
        server.respond { request in
            if request.path.hasSuffix("/refresh") { return .json(200, renewed) }
            return request.headers["Authorization"] == "Bearer \(second)" ? .json(200, codeBody) : StubResponse(status: 401)
        }
        let store = InMemoryTokenStore(token: first, refreshToken: "refresh-1")

        let code = try await server.backend(tokenStore: store).deviceLinkCode()

        #expect(code.code == "one-time-code")
        #expect(server.requests.map(\.path) == [
            "/api/users/10/identity/device-link",
            "/api/identity/auth/refresh",
            "/api/users/10/identity/device-link",
        ])
        #expect(try body(try #require(server.requests.last)) == ["refreshToken": "refresh-2"])
    }

    @Test("a second 401 is given up on, with no third try")
    func givesUpAfterSecondRefusal() async throws {
        let server = StubServer()
        let first = makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600))
        let renewed = pair(makeToken(userId: 10, expiresAt: Date().addingTimeInterval(7200)), "refresh-2")
        server.respond { request in
            request.path.hasSuffix("/refresh") ? .json(200, renewed) : StubResponse(status: 401)
        }
        let store = InMemoryTokenStore(token: first, refreshToken: "refresh-1")

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).deviceLinkCode()
        }
        #expect(server.requests.count == 3)
    }

    @Test("with nobody signed in, no request is made")
    func signedOutAsksNothing() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend().deviceLinkCode()
        }
        #expect(server.requests.isEmpty)
    }

    @Test("a session with no refresh token cannot ask for a code")
    func needsRefreshToken() async throws {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }
        let store = InMemoryTokenStore(token: makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600)))

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).deviceLinkCode()
        }
        #expect(server.requests.isEmpty)
    }

    // MARK: The watch redeems it

    @Test("redeeming posts the code with no token and returns the new session")
    func redeemReturnsSession() async throws {
        let server = StubServer()
        let renewed = pair("watch-access", "watch-refresh")
        server.respond { _ in .json(200, renewed) }

        let tokens = try await server.backend(tokenStore: InMemoryTokenStore(token: "stale")).redeemDeviceLink(code: "one-time-code")

        #expect(tokens == SessionTokens(accessToken: "watch-access", refreshToken: "watch-refresh"))
        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/identity/auth/device-link")
        #expect(request.headers["Authorization"] == nil)
        #expect(try body(request) == ["code": "one-time-code"])
    }

    @Test("a refused code is a 401, and nothing is stored by the client")
    func redeemRefused() async throws {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 401) }
        let store = InMemoryTokenStore()

        await #expect(throws: BackendError.unauthorized) {
            try await server.backend(tokenStore: store).redeemDeviceLink(code: "used-code")
        }
        #expect(store.read() == nil)
    }

    // MARK: The messages between the devices

    @Test("each answer survives the trip across the link")
    func repliesRoundTrip() {
        for reply in [WatchLinkReply.code("abc"), .signedOut, .failed] {
            #expect(WatchLinkReply(message: reply.message) == reply)
        }
    }

    @Test("an answer the watch does not understand reads as a failure")
    func unknownReplyIsFailure() {
        #expect(WatchLinkReply(message: [:]) == .failed)
        #expect(WatchLinkReply(message: ["code": ""]) == .failed)
        #expect(WatchLinkReply(message: ["error": "something-new"]) == .failed)
    }

    @Test("the answer never carries a token")
    func replyCarriesOnlyTheCode() {
        #expect(WatchLinkReply.code("abc").message == ["code": "abc"])
    }

    // MARK: The phone answers the watch

    @Test("the phone answers a connect request with a code")
    func responderAnswersWithCode() async {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }
        let store = InMemoryTokenStore(token: makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600)), refreshToken: "refresh-1")
        let responder = WatchLinkResponder(backend: server.backend(tokenStore: store))

        #expect(await responder.reply(to: WatchLink.request) == .code("one-time-code"))
    }

    @Test("a signed-out phone says so and calls nothing")
    func responderSignedOut() async {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }
        let responder = WatchLinkResponder(backend: server.backend())

        #expect(await responder.reply(to: WatchLink.request) == .signedOut)
        #expect(server.requests.isEmpty)
    }

    @Test("a phone that cannot reach the server answers that it failed")
    func responderOffline() async {
        let server = StubServer()
        server.respond(nil)
        let store = InMemoryTokenStore(token: makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600)), refreshToken: "refresh-1")
        let responder = WatchLinkResponder(backend: server.backend(tokenStore: store))

        #expect(await responder.reply(to: WatchLink.request) == .failed)
    }

    @Test("a rate-limited phone answers that it failed")
    func responderRateLimited() async {
        let server = StubServer()
        server.respond { _ in StubResponse(status: 429, headers: ["Retry-After": "60"]) }
        let store = InMemoryTokenStore(token: makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600)), refreshToken: "refresh-1")
        let responder = WatchLinkResponder(backend: server.backend(tokenStore: store))

        #expect(await responder.reply(to: WatchLink.request) == .failed)
    }

    @Test("a message that is not a connect request gets no answer and no server call")
    func responderIgnoresOtherMessages() async {
        let server = StubServer()
        server.respond { _ in .json(200, codeBody) }
        let store = InMemoryTokenStore(token: makeToken(userId: 10, expiresAt: Date().addingTimeInterval(3600)), refreshToken: "refresh-1")
        let responder = WatchLinkResponder(backend: server.backend(tokenStore: store))

        #expect(await responder.reply(to: ["fitt.request": "something-else"]) == nil)
        #expect(await responder.reply(to: [:]) == nil)
        #expect(server.requests.isEmpty)
    }
}
