import FittAPI
import Foundation

/// Answers the watch when it asks to be connected.
///
/// It holds the same `Backend` the screens use. Two backends would each renew
/// the session by themselves, and the server reads a refresh token used twice
/// as theft and ends the login.
public struct WatchLinkResponder: Sendable {
    private let backend: Backend

    public init(backend: Backend) {
        self.backend = backend
    }

    /// The answer to send back, or nil for a message that is not a connect request.
    public func reply(to message: [String: String]) async -> WatchLinkReply? {
        guard WatchLink.isRequest(message) else { return nil }
        do {
            return .code(try await backend.deviceLinkCode().code)
        } catch BackendError.unauthorized {
            return .signedOut
        } catch {
            return .failed
        }
    }
}

#if os(iOS) && canImport(WatchConnectivity)
import WatchConnectivity

/// The phone's end of the link to the paired watch.
///
/// The app target creates one at launch, before any screen exists. A request
/// from the watch starts the phone app in the background, where no view is
/// ever shown, so this cannot live in a view.
public final class PhoneWatchSession: NSObject, WCSessionDelegate, Sendable {
    private let responder: WatchLinkResponder

    public init(responder: WatchLinkResponder) {
        self.responder = responder
    }

    /// Does nothing on a device that cannot pair with a watch.
    public func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    public func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: (any Error)?) {}

    public func sessionDidBecomeInactive(_ session: WCSession) {}

    /// The user switched to another paired watch. Activating again connects to it.
    public func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    public func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let request = message.compactMapValues { $0 as? String }
        let answer = ReplyHandler(send: replyHandler)
        Task {
            let reply = await responder.reply(to: request) ?? .failed
            answer.send(reply.message)
        }
    }

    /// The system's reply block is not marked Sendable, and it is called once.
    private struct ReplyHandler: @unchecked Sendable {
        let send: ([String: Any]) -> Void
    }
}
#endif
