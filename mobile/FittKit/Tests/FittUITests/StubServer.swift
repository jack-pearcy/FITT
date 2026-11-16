import Foundation
@testable import FittUI

/// A canned HTTP response for one request.
struct StubResponse: Sendable {
    var status: Int
    var headers: [String: String] = [:]
    var body: String = ""

    static func json(_ status: Int, _ body: String) -> StubResponse {
        StubResponse(status: status, headers: ["Content-Type": "application/json"], body: body)
    }
}

/// What the code under test actually sent.
struct RecordedRequest: Sendable {
    let method: String
    let path: String
    let headers: [String: String]
    let body: Data
}

/// Stands in for the FITT API. Each test gets its own host name, so tests can run
/// in parallel without seeing each other's requests.
final class StubServer: Sendable {
    let host = "stub-\(UUID().uuidString.lowercased()).test"

    var config: BackendConfig { BackendConfig(baseURL: URL(string: "https://\(host)")!) }

    var session: URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: configuration)
    }

    /// Answers every request with the result of `handler`. Passing nil makes requests fail to connect.
    func respond(_ handler: (@Sendable (RecordedRequest) -> StubResponse)?) {
        StubProtocol.state.withLock { $0.handlers[host] = handler }
    }

    var requests: [RecordedRequest] {
        StubProtocol.state.withLock { $0.requests[host] ?? [] }
    }

    func backend(tokenStore: any TokenStore = InMemoryTokenStore()) -> Backend {
        Backend(config: config, tokenStore: tokenStore, session: session)
    }
}

final class StubProtocol: URLProtocol, @unchecked Sendable {
    struct State {
        var handlers: [String: @Sendable (RecordedRequest) -> StubResponse] = [:]
        var requests: [String: [RecordedRequest]] = [:]
    }

    static let state = Locked(State())

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        guard let url = request.url, let host = url.host() else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        let recorded = RecordedRequest(
            method: request.httpMethod ?? "GET",
            path: url.path(),
            headers: request.allHTTPHeaderFields ?? [:],
            body: Self.body(of: request)
        )
        let handler = Self.state.withLock { state in
            state.requests[host, default: []].append(recorded)
            return state.handlers[host]
        }
        guard let handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
            return
        }
        let stub = handler(recorded)
        let response = HTTPURLResponse(url: url, statusCode: stub.status, httpVersion: "HTTP/1.1", headerFields: stub.headers)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(stub.body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    /// URLSession hands a protocol the body as a stream, not as `httpBody`.
    private static func body(of request: URLRequest) -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return Data() }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

/// A value guarded by a lock, so static test state is safe under Swift 6 checking.
final class Locked<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value

    init(_ value: Value) { self.value = value }

    func withLock<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body(&value)
    }
}

/// Builds an unsigned token shaped like the server's, with the given claims.
func makeToken(userId: Int64, expiresAt: Date) -> String {
    func segment(_ text: String) -> String {
        Data(text.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    let payload = #"{"iss":"fitt-api","uid":\#(userId),"exp":\#(Int(expiresAt.timeIntervalSince1970))}"#
    return [segment(#"{"alg":"HS256"}"#), segment(payload), "signature"].joined(separator: ".")
}
