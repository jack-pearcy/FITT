import FittAPI
import FittCore
import Foundation
import Testing
@testable import FittWatch

@MainActor
@Suite struct WatchModelTests {
    @Test("a watch with no stored session asks for the phone")
    func noSession() {
        let model = WatchModel(tokenStore: InMemoryTokenStore())

        #expect(model.phase == .checking)
        model.start()

        #expect(model.phase == .notConnected)
    }

    @Test("a stored session connects the watch as that user")
    func storedSession() {
        let token = makeToken(userId: 42, expiresAt: Date().addingTimeInterval(3600))
        let model = WatchModel(tokenStore: InMemoryTokenStore(token: token, refreshToken: "refresh"))

        model.start()

        #expect(model.phase == .connected(userId: 42))
    }

    @Test("an expired access token still counts, because the refresh token renews it")
    func expiredAccessToken() {
        let token = makeToken(userId: 7, expiresAt: Date().addingTimeInterval(-3600))
        let model = WatchModel(tokenStore: InMemoryTokenStore(token: token, refreshToken: "refresh"))

        model.start()

        #expect(model.phase == .connected(userId: 7))
    }

    @Test("a stored token that cannot be read is treated as no session")
    func unreadableToken() {
        let model = WatchModel(tokenStore: InMemoryTokenStore(token: "not-a-token"))

        model.start()

        #expect(model.phase == .notConnected)
    }

    @Test("starting again picks up a session the phone handed over in the meantime")
    func connectedLater() {
        let store = InMemoryTokenStore()
        let model = WatchModel(tokenStore: store)
        model.start()
        #expect(model.phase == .notConnected)

        store.save(SessionTokens(
            accessToken: makeToken(userId: 9, expiresAt: Date().addingTimeInterval(3600)),
            refreshToken: "refresh"))
        model.start()

        #expect(model.phase == .connected(userId: 9))
    }

    /// An unsigned token with the two claims the app reads. The app never checks
    /// the signature; the server does.
    private func makeToken(userId: Int64, expiresAt: Date) -> String {
        func segment(_ text: String) -> String {
            Data(text.utf8).base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
        }
        let payload = "{\"uid\":\(userId),\"exp\":\(Int(expiresAt.timeIntervalSince1970))}"
        return [segment("{\"alg\":\"HS256\"}"), segment(payload), segment("signature")].joined(separator: ".")
    }
}
