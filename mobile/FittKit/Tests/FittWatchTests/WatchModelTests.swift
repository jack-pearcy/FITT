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

    // MARK: Connecting through the phone

    @Test("a code from the phone is traded for a session, which is stored")
    func connects() async {
        let store = InMemoryTokenStore()
        let token = makeToken(userId: 5, expiresAt: Date().addingTimeInterval(3600))
        let redeemed = Recorder()
        let model = WatchModel(tokenStore: store, phone: FakePhone(.success("one-time-code"))) { code in
            redeemed.add(code)
            return SessionTokens(accessToken: token, refreshToken: "watch-refresh")
        }
        model.start()

        await model.connect()

        #expect(model.phase == .connected(userId: 5))
        #expect(model.problem == nil)
        #expect(model.isConnecting == false)
        #expect(redeemed.values == ["one-time-code"])
        #expect(store.read() == SessionTokens(accessToken: token, refreshToken: "watch-refresh"))
    }

    @Test("a phone out of reach leaves the watch not connected and says so")
    func phoneUnreachable() async {
        let redeemed = Recorder()
        let model = WatchModel(tokenStore: InMemoryTokenStore(), phone: FakePhone(.failure(.unreachable))) { code in
            redeemed.add(code)
            return SessionTokens(accessToken: "unused")
        }
        model.start()

        await model.connect()

        #expect(model.phase == .notConnected)
        #expect(model.problem == .phoneUnreachable)
        #expect(redeemed.values.isEmpty)
    }

    @Test("a signed-out phone is named as the reason")
    func phoneSignedOut() async {
        let model = WatchModel(tokenStore: InMemoryTokenStore(), phone: FakePhone(.failure(.signedOut))) { _ in
            SessionTokens(accessToken: "unused")
        }
        model.start()

        await model.connect()

        #expect(model.phase == .notConnected)
        #expect(model.problem == .phoneSignedOut)
    }

    @Test("a code the server refuses stores nothing")
    func serverRefusesCode() async {
        let store = InMemoryTokenStore()
        let model = WatchModel(tokenStore: store, phone: FakePhone(.success("stale-code"))) { _ in
            throw BackendError.unauthorized
        }
        model.start()

        await model.connect()

        #expect(model.phase == .notConnected)
        #expect(model.problem == .failed)
        #expect(store.read() == nil)
    }

    @Test("a session whose token cannot be read is not stored")
    func unreadableSession() async {
        let store = InMemoryTokenStore()
        let model = WatchModel(tokenStore: store, phone: FakePhone(.success("code"))) { _ in
            SessionTokens(accessToken: "not-a-token", refreshToken: "refresh")
        }
        model.start()

        await model.connect()

        #expect(model.phase == .notConnected)
        #expect(model.problem == .failed)
        #expect(store.read() == nil)
    }

    @Test("a try after a failure clears the old reason when it works")
    func retryClearsProblem() async {
        let phone = FakePhone(.failure(.unreachable))
        let token = makeToken(userId: 3, expiresAt: Date().addingTimeInterval(3600))
        let model = WatchModel(tokenStore: InMemoryTokenStore(), phone: phone) { _ in
            SessionTokens(accessToken: token, refreshToken: "refresh")
        }
        model.start()
        await model.connect()
        #expect(model.problem == .phoneUnreachable)

        phone.answer = .success("code")
        await model.connect()

        #expect(model.phase == .connected(userId: 3))
        #expect(model.problem == nil)
    }

    @Test("a connected watch never asks the phone again")
    func connectedDoesNotAsk() async {
        let phone = FakePhone(.success("code"))
        let token = makeToken(userId: 42, expiresAt: Date().addingTimeInterval(3600))
        let model = WatchModel(tokenStore: InMemoryTokenStore(token: token, refreshToken: "refresh"), phone: phone) { _ in
            SessionTokens(accessToken: "unused")
        }
        model.start()

        await model.connect()

        #expect(phone.asked == 0)
        #expect(model.phase == .connected(userId: 42))
    }

    @Test("with no link to a phone, connecting does nothing")
    func noPhoneLink() async {
        let model = WatchModel(tokenStore: InMemoryTokenStore())
        model.start()

        await model.connect()

        #expect(model.phase == .notConnected)
        #expect(model.problem == nil)
    }

    @Test("each reason the watch is not connected has its own words")
    func notConnectedWords() {
        let problems: [WatchModel.Problem?] = [nil, .phoneUnreachable, .phoneSignedOut, .failed]
        let titles = Set(problems.map(NotConnectedText.title(for:)))
        let details = Set(problems.map(NotConnectedText.detail(for:)))

        // No reason yet and an unreachable phone share their words on purpose.
        #expect(titles.count == 3)
        #expect(details.count == 3)
        #expect(NotConnectedText.title(for: .phoneSignedOut) == "Sign in on your iPhone")
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

/// Stands in for the paired phone.
private final class FakePhone: PhoneLink, @unchecked Sendable {
    private let lock = NSLock()
    private var _answer: Result<String, PhoneLinkError>
    private var _asked = 0

    init(_ answer: Result<String, PhoneLinkError>) {
        _answer = answer
    }

    var answer: Result<String, PhoneLinkError> {
        get { lock.withLock { _answer } }
        set { lock.withLock { _answer = newValue } }
    }

    var asked: Int { lock.withLock { _asked } }

    let becameReachable = AsyncStream<Void> { $0.finish() }

    func requestCode() async throws(PhoneLinkError) -> String {
        let current = lock.withLock {
            _asked += 1
            return _answer
        }
        return try current.get()
    }
}

/// Collects what a `@Sendable` closure was called with.
private final class Recorder: @unchecked Sendable {
    private let lock = NSLock()
    private var _values: [String] = []

    var values: [String] { lock.withLock { _values } }

    func add(_ value: String) {
        lock.withLock { _values.append(value) }
    }
}
