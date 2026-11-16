import Foundation

/// One logged set. Mirrors the backend `SetEntryResponse` record.
///
/// `weight` and `rpe` are `Decimal` because the server stores them as exact
/// decimals (decimal(6,2) and decimal(3,1)). `Double` cannot hold 102.35 exactly.
public struct SetEntry: Codable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let setNumber: Int
    public let reps: Int?
    public let weight: Decimal?
    public let rpe: Decimal?
    public let completed: Bool

    public init(id: Int64, setNumber: Int, reps: Int?, weight: Decimal?, rpe: Decimal?, completed: Bool) {
        self.id = id
        self.setNumber = setNumber
        self.reps = reps
        self.weight = weight
        self.rpe = rpe
        self.completed = completed
    }
}
