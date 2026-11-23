import FittAPI
import FittCore
import Foundation
import Observation

/// What the watch app knows and shows. Screens read it and call its methods.
///
/// The watch never asks for a password: typing one on a wrist is miserable, and
/// the phone is already signed in. The watch asks the phone for a short-lived
/// code, trades it with the server for a session of its own, and keeps that
/// session in its own Keychain.
@MainActor
@Observable
public final class WatchModel {
    public enum Phase: Equatable, Sendable {
        /// Before the Keychain has been read.
        case checking
        /// No session on this watch. The phone has to help connect it.
        case notConnected
        /// A session for this user is stored on the watch.
        case connected(userId: Int64)
    }

    /// Why the last try to connect did not work.
    public enum Problem: Equatable, Sendable {
        /// The phone did not answer.
        case phoneUnreachable
        /// Nobody is signed in on the phone.
        case phoneSignedOut
        /// The phone or the server could not finish it.
        case failed
    }

    public private(set) var phase: Phase = .checking
    /// True while a try to connect is running.
    public private(set) var isConnecting = false
    /// Set by a failed try, cleared by the next one.
    public private(set) var problem: Problem?

    private let tokenStore: any TokenStore
    private let phone: (any PhoneLink)?
    private let redeem: @Sendable (String) async throws -> SessionTokens

    /// - Parameters:
    ///   - phone: the link to the paired phone. Nil leaves the watch unable to
    ///     connect by itself, which is what previews get.
    ///   - redeem: trades a code for a session, normally `Backend.redeemDeviceLink`.
    public init(
        tokenStore: any TokenStore,
        phone: (any PhoneLink)? = nil,
        redeem: @escaping @Sendable (String) async throws -> SessionTokens = { _ in throw BackendError.transport }
    ) {
        self.tokenStore = tokenStore
        self.phone = phone
        self.redeem = redeem
    }

    /// Reads the stored session, if any. Safe to call again, for example each
    /// time the app comes to the front: the session may have ended on the
    /// server in the meantime, which clears the stored one.
    public func start() {
        guard let tokens = try? tokenStore.read(),
              let claims = try? TokenClaims(token: tokens.accessToken) else {
            phase = .notConnected
            return
        }
        // An expired access token still counts: the refresh token renews it on
        // the first request, the same way it does on the phone.
        phase = .connected(userId: claims.userId)
        problem = nil
    }

    /// Asks the phone for a code and trades it for this watch's own session.
    /// Does nothing when the watch is connected or a try is already running.
    public func connect() async {
        guard phase == .notConnected, !isConnecting, let phone else { return }
        isConnecting = true
        defer { isConnecting = false }
        do {
            let code = try await phone.requestCode()
            let tokens = try await redeem(code)
            guard let claims = try? TokenClaims(token: tokens.accessToken) else {
                problem = .failed
                return
            }
            try tokenStore.save(tokens)
            problem = nil
            phase = .connected(userId: claims.userId)
        } catch PhoneLinkError.unreachable {
            problem = .phoneUnreachable
        } catch PhoneLinkError.signedOut {
            problem = .phoneSignedOut
        } catch {
            problem = .failed
        }
    }
}
