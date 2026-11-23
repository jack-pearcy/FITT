import FittAPI
import FittCore
import Foundation
import Observation

/// What the watch app knows and shows. Screens read it and call its methods.
///
/// The watch never asks for a password: typing one on a wrist is miserable, and
/// the phone is already signed in. The watch is connected once the phone has
/// handed it a session of its own, which it keeps in its own Keychain.
@MainActor
@Observable
public final class WatchModel {
    public enum Phase: Equatable, Sendable {
        /// Before the Keychain has been read.
        case checking
        /// No session on this watch. The phone has to hand one over.
        case notConnected
        /// A session for this user is stored on the watch.
        case connected(userId: Int64)
    }

    public private(set) var phase: Phase = .checking

    private let tokenStore: any TokenStore

    public init(tokenStore: any TokenStore) {
        self.tokenStore = tokenStore
    }

    /// Reads the stored session, if any. Safe to call again, for example each
    /// time the app comes to the front, since the phone may have connected or
    /// signed out in the meantime.
    public func start() {
        guard let tokens = try? tokenStore.read(),
              let claims = try? TokenClaims(token: tokens.accessToken) else {
            phase = .notConnected
            return
        }
        // An expired access token still counts: the refresh token renews it on
        // the first request, the same way it does on the phone.
        phase = .connected(userId: claims.userId)
    }
}
