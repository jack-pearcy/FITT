import Foundation

/// The two messages the watch and the phone exchange to connect the watch.
///
/// The watch asks, the phone answers. The answer carries a code that works
/// once and for a minute, never a token: nothing long-lived crosses the link
/// between the devices. Both sides are plain string dictionaries, which is
/// what the paired-device link carries.
public enum WatchLink {
    /// What the watch sends when it has no session.
    public static let request: [String: String] = ["fitt.request": "device-link"]

    public static func isRequest(_ message: [String: String]) -> Bool {
        message["fitt.request"] == "device-link"
    }
}

/// The phone's answer to `WatchLink.request`.
public enum WatchLinkReply: Equatable, Sendable {
    /// A code the watch can trade with the server for its own session.
    case code(String)
    /// Nobody is signed in on the phone.
    case signedOut
    /// The phone could not get a code: no connection, a locked phone, or the
    /// server refused.
    case failed

    public var message: [String: String] {
        switch self {
        case .code(let code): ["code": code]
        case .signedOut: ["error": "signed-out"]
        case .failed: ["error": "failed"]
        }
    }

    /// Anything that is not a code or the signed-out answer reads as a failure,
    /// so an answer from a newer or older phone app never crashes the watch.
    public init(message: [String: String]) {
        if let code = message["code"], !code.isEmpty {
            self = .code(code)
        } else if message["error"] == "signed-out" {
            self = .signedOut
        } else {
            self = .failed
        }
    }
}
