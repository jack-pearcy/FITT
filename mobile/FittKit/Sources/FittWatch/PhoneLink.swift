import FittAPI
import Foundation

/// Why the phone gave the watch no code.
public enum PhoneLinkError: Error, Equatable, Sendable {
    /// The phone is out of range, off, or the link is not up yet.
    case unreachable
    /// Nobody is signed in on the phone.
    case signedOut
    /// The phone answered that it could not get a code.
    case failed
}

/// The watch's way to ask the paired phone for a connect code.
public protocol PhoneLink: Sendable {
    /// A code the server will trade for a session, good for about a minute.
    func requestCode() async throws(PhoneLinkError) -> String
    /// Yields each time the phone comes into reach, so the watch can try then.
    var becameReachable: AsyncStream<Void> { get }
}

#if canImport(WatchConnectivity)
import WatchConnectivity

/// `PhoneLink` over the system's paired-device link.
///
/// Sending a message from the watch starts the phone app in the background if
/// it is not running, so the user does not have to open it first as long as
/// the phone is nearby and signed in.
public final class WatchConnectivityPhoneLink: NSObject, PhoneLink, WCSessionDelegate, @unchecked Sendable {
    public let becameReachable: AsyncStream<Void>
    private let reachable: AsyncStream<Void>.Continuation

    private let lock = NSLock()
    private var activationFinished = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    public override init() {
        (becameReachable, reachable) = AsyncStream.makeStream(bufferingPolicy: .bufferingNewest(1))
        super.init()
        guard WCSession.isSupported() else {
            activationFinished = true
            return
        }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    public func requestCode() async throws(PhoneLinkError) -> String {
        await activation()
        guard WCSession.isSupported() else { throw .unreachable }
        let session = WCSession.default
        guard session.activationState == .activated, session.isReachable else { throw .unreachable }

        let reply: WatchLinkReply? = await withCheckedContinuation { continuation in
            session.sendMessage(WatchLink.request, replyHandler: { answer in
                continuation.resume(returning: WatchLinkReply(message: answer.compactMapValues { $0 as? String }))
            }, errorHandler: { _ in
                continuation.resume(returning: nil)
            })
        }
        switch reply {
        case .code(let code): return code
        case .signedOut: throw .signedOut
        case .failed: throw .failed
        case nil: throw .unreachable
        }
    }

    /// Returns once the system has finished activating the link, whatever the result.
    private func activation() async {
        await withCheckedContinuation { continuation in
            let finished = lock.withLock {
                if !activationFinished { waiters.append(continuation) }
                return activationFinished
            }
            if finished { continuation.resume() }
        }
    }

    public func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: (any Error)?) {
        let waiting = lock.withLock {
            activationFinished = true
            defer { waiters = [] }
            return waiters
        }
        waiting.forEach { $0.resume() }
        if session.isReachable { reachable.yield() }
    }

    public func sessionReachabilityDidChange(_ session: WCSession) {
        if session.isReachable { reachable.yield() }
    }

    #if os(iOS)
    // Required of every delegate on iOS. This type is only used on the watch.
    public func sessionDidBecomeInactive(_ session: WCSession) {}
    public func sessionDidDeactivate(_ session: WCSession) {}
    #endif
}
#endif
