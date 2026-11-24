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

    /// Why the last read or write of today's training did not work.
    public enum TodayProblem: Equatable, Sendable {
        /// No connection to the server.
        case offline
        /// The server answered with an error.
        case failed
    }

    public private(set) var phase: Phase = .checking
    /// True while a try to connect is running.
    public private(set) var isConnecting = false
    /// Set by a failed try, cleared by the next one.
    public private(set) var problem: Problem?

    /// What the server says to train next. Nil until loaded.
    public private(set) var nextLift: NextLift?
    /// A workout started and not finished, on this watch or on the phone.
    public private(set) var activeSession: WorkoutSession?
    /// True once today's training has been read at least once since connecting.
    public private(set) var hasLoadedToday = false
    public private(set) var isLoadingToday = false
    public private(set) var isStarting = false
    /// Set by a failed read or start, cleared by the next one that works.
    public private(set) var todayProblem: TodayProblem?

    private let tokenStore: any TokenStore
    private let phone: (any PhoneLink)?
    private let backend: (any WatchBackend)?
    private let now: @Sendable () -> Date

    /// - Parameters:
    ///   - phone: the link to the paired phone. Nil leaves the watch unable to
    ///     connect by itself, which is what previews get.
    ///   - backend: the server. Nil leaves the watch unable to connect or load.
    public init(
        tokenStore: any TokenStore,
        phone: (any PhoneLink)? = nil,
        backend: (any WatchBackend)? = nil,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.tokenStore = tokenStore
        self.phone = phone
        self.backend = backend
        self.now = now
    }

    private var userId: Int64? {
        if case .connected(let userId) = phase { userId } else { nil }
    }

    // MARK: Connecting

    /// Reads the stored session, if any. Safe to call again, for example each
    /// time the app comes to the front: the session may have ended on the
    /// server in the meantime, which clears the stored one.
    public func start() {
        guard let tokens = try? tokenStore.read(),
              let claims = try? TokenClaims(token: tokens.accessToken) else {
            phase = .notConnected
            forgetToday()
            return
        }
        // An expired access token still counts: the refresh token renews it on
        // the first request, the same way it does on the phone.
        if userId != claims.userId { forgetToday() }
        phase = .connected(userId: claims.userId)
        problem = nil
    }

    /// Asks the phone for a code and trades it for this watch's own session.
    /// Does nothing when the watch is connected or a try is already running.
    public func connect() async {
        guard phase == .notConnected, !isConnecting, let phone, let backend else { return }
        isConnecting = true
        defer { isConnecting = false }
        do {
            let code = try await phone.requestCode()
            let tokens = try await backend.redeemDeviceLink(code: code)
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

    // MARK: Today

    /// Reads what to train next and whether a workout is already going. Both
    /// are read together, so the screen never offers Start beside a workout
    /// that is in progress. What was shown stays when the read fails.
    public func loadToday() async {
        guard let userId, let backend, !isLoadingToday else { return }
        isLoadingToday = true
        defer { isLoadingToday = false }
        do {
            async let next = backend.nextLift(userId: userId)
            async let sessions = backend.sessions(userId: userId)
            let (loadedNext, loadedSessions) = try await (next, sessions)
            nextLift = loadedNext
            activeSession = loadedSessions.first { !$0.isFinished }
            hasLoadedToday = true
            todayProblem = nil
        } catch {
            handle(error)
        }
    }

    /// Starts a session for the day the server says is next and adds that day's
    /// exercises to it. If an exercise fails to add, the session that was made
    /// is kept and shown, so nothing is started twice.
    public func startWorkout() async {
        guard let userId, let backend, !isStarting, activeSession == nil,
              let next = nextLift, let day = next.day else { return }
        isStarting = true
        defer { isStarting = false }
        do {
            var session = try await backend.startSession(
                NewWorkoutSession(startedAt: now(), planId: next.planId), userId: userId)
            activeSession = session
            for item in day.orderedExercises {
                session = try await backend.addEntry(exerciseId: item.exercise.id, sessionId: session.id, userId: userId)
                activeSession = session
            }
            todayProblem = nil
        } catch {
            handle(error)
        }
    }

    /// A 401 that survived a refresh means the server ended this watch's
    /// session, and the client has cleared the stored tokens: read them again,
    /// which sends the watch back to the connect screen.
    private func handle(_ error: any Error) {
        switch error as? BackendError {
        case .unauthorized:
            start()
        case .transport:
            todayProblem = .offline
        default:
            todayProblem = .failed
        }
    }

    private func forgetToday() {
        nextLift = nil
        activeSession = nil
        hasLoadedToday = false
        todayProblem = nil
    }
}
