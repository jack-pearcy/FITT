import FittAPI
import FittCore
import Foundation

/// The server calls the watch makes. `Backend` provides all of them; tests use a stand-in.
public protocol WatchBackend: Sendable {
    func redeemDeviceLink(code: String) async throws -> SessionTokens
    func nextLift(userId: Int64) async throws -> NextLift
    func sessions(userId: Int64) async throws -> [WorkoutSession]
    func startSession(_ session: NewWorkoutSession, userId: Int64) async throws -> WorkoutSession
    func addEntry(exerciseId: Int64, sessionId: Int64, userId: Int64) async throws -> WorkoutSession
}

extension Backend: WatchBackend {}
