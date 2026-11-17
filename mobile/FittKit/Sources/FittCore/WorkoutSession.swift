import Foundation

/// Reads and writes the server's `Instant` values: ISO 8601 in UTC, with up to
/// nine digits of fractional seconds or none, such as `2026-11-16T05:20:32.742695Z`.
public enum ServerInstant {
    public static func parse(_ text: String) -> Date? {
        // Foundation's parser is strict about the fraction, so it is split off
        // and added back as seconds.
        var whole = text
        var fraction: TimeInterval = 0
        if let dot = text.firstIndex(of: ".") {
            let afterDot = text[text.index(after: dot)...]
            let digits = afterDot.prefix { $0.isASCII && $0.isNumber }
            guard !digits.isEmpty, let value = TimeInterval("0." + digits) else { return nil }
            fraction = value
            whole = String(text[..<dot]) + afterDot.dropFirst(digits.count)
        }
        guard let date = try? Date(whole, strategy: .iso8601) else { return nil }
        return date.addingTimeInterval(fraction)
    }

    /// Whole seconds in UTC, which the server accepts.
    public static func format(_ date: Date) -> String {
        date.formatted(.iso8601)
    }
}

/// Mirrors the backend `ExerciseEntryResponse` record: one exercise inside a session.
public struct ExerciseEntry: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let orderIndex: Int
    public let exercise: Exercise
    public let sets: [SetEntry]

    public init(id: Int64, orderIndex: Int, exercise: Exercise, sets: [SetEntry]) {
        self.id = id
        self.orderIndex = orderIndex
        self.exercise = exercise
        self.sets = sets
    }

    public var orderedSets: [SetEntry] {
        sets.sorted { $0.setNumber < $1.setNumber }
    }
}

/// Mirrors the backend `WorkoutSessionResponse` record.
public struct WorkoutSession: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let planId: Int64?
    public let startedAt: Date
    /// Nil while the session is in progress.
    public let finishedAt: Date?
    /// Nil when nothing was written. The server never stores blank text.
    public let notes: String?
    public let exerciseEntries: [ExerciseEntry]

    public init(id: Int64, planId: Int64?, startedAt: Date, finishedAt: Date?, notes: String? = nil, exerciseEntries: [ExerciseEntry]) {
        self.id = id
        self.planId = planId
        self.startedAt = startedAt
        self.finishedAt = finishedAt
        self.notes = notes
        self.exerciseEntries = exerciseEntries
    }

    public var isFinished: Bool { finishedAt != nil }

    /// The server numbers a new entry by how many the session has, so after one
    /// is removed two can share an index. The id breaks the tie: the newer entry
    /// comes last.
    public var orderedEntries: [ExerciseEntry] {
        exerciseEntries.sorted { ($0.orderIndex, $0.id) < ($1.orderIndex, $1.id) }
    }

    public var loggedSetCount: Int {
        exerciseEntries.reduce(0) { $0 + $1.sets.count }
    }

    /// How long the workout took. Nil while it is in progress, and nil when the
    /// stored times are out of order, which would otherwise show a negative length.
    public var duration: TimeInterval? {
        guard let finishedAt, finishedAt >= startedAt else { return nil }
        return finishedAt.timeIntervalSince(startedAt)
    }

    private enum CodingKeys: String, CodingKey {
        case id, planId, startedAt, finishedAt, notes, exerciseEntries
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int64.self, forKey: .id)
        planId = try container.decodeIfPresent(Int64.self, forKey: .planId)
        notes = try container.decodeIfPresent(String.self, forKey: .notes)
        exerciseEntries = try container.decode([ExerciseEntry].self, forKey: .exerciseEntries)

        let started = try container.decode(String.self, forKey: .startedAt)
        guard let startedAt = ServerInstant.parse(started) else {
            throw DecodingError.dataCorruptedError(forKey: .startedAt, in: container, debugDescription: "Not an instant: \(started)")
        }
        self.startedAt = startedAt

        if let finished = try container.decodeIfPresent(String.self, forKey: .finishedAt) {
            guard let finishedAt = ServerInstant.parse(finished) else {
                throw DecodingError.dataCorruptedError(forKey: .finishedAt, in: container, debugDescription: "Not an instant: \(finished)")
            }
            self.finishedAt = finishedAt
        } else {
            finishedAt = nil
        }
    }
}

/// The session list arranged for the history screen.
public enum SessionHistory {
    /// The finished sessions, newest first. The one in progress is left out
    /// because it is shown as the current workout.
    public static func finished(_ sessions: [WorkoutSession]) -> [WorkoutSession] {
        sessions
            .filter(\.isFinished)
            .sorted { ($0.startedAt, $0.id) > ($1.startedAt, $1.id) }
    }
}

/// The body of `POST /api/users/{id}/workout-sessions`.
public struct NewWorkoutSession: Encodable, Equatable, Sendable {
    public let startedAt: String
    public let planId: Int64?

    public init(startedAt: Date, planId: Int64?) {
        self.startedAt = ServerInstant.format(startedAt)
        self.planId = planId
    }
}

/// The body of `PUT .../notes`. Built from what was typed: the text is trimmed,
/// and blank text becomes nil, which clears the notes on the server.
public struct SessionNotes: Encodable, Equatable, Sendable {
    public static let maxLength = 5000

    public let notes: String?

    public init(text: String) throws(ValidationFailure) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count > Self.maxLength {
            throw ValidationFailure(fields: ["notes": "Notes must be \(Self.maxLength) characters or fewer"])
        }
        notes = trimmed.isEmpty ? nil : trimmed
    }

    /// Nil is sent as an explicit null, so the body always names the field.
    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(notes, forKey: .notes)
    }

    private enum CodingKeys: String, CodingKey {
        case notes
    }
}

/// The body of `POST .../entries`.
public struct NewExerciseEntry: Encodable, Equatable, Sendable {
    public let exerciseId: Int64

    public init(exerciseId: Int64) {
        self.exerciseId = exerciseId
    }
}

/// The body of `POST .../entries/{entryId}/sets`. Mirrors the backend `LogSetRequest` record.
public struct LoggedSet: Encodable, Equatable, Sendable {
    public let reps: Int
    public let weight: Decimal?
    public let rpe: Decimal?

    public init(reps: Int, weight: Decimal? = nil, rpe: Decimal? = nil) {
        self.reps = reps
        self.weight = weight
        self.rpe = rpe
    }
}

/// One set as typed. Reps are required; weight is empty for a bodyweight set,
/// and RPE is optional.
public struct SetDraft: Equatable, Sendable {
    public static let reps = 1...1000
    public static let weight: ClosedRange<Decimal> = 0...Decimal(string: "9999.99")!
    public static let rpe: ClosedRange<Decimal> = 1...10

    public var weight = ""
    public var reps = ""
    public var rpe = ""

    public init(weight: String = "", reps: String = "", rpe: String = "") {
        self.weight = weight
        self.reps = reps
        self.rpe = rpe
    }

    public func loggedSet() throws(ValidationFailure) -> LoggedSet {
        var problems: [String: String] = [:]

        let repsValue = Int(reps.trimmingCharacters(in: .whitespaces))
        if repsValue == nil || !Self.reps.contains(repsValue!) {
            problems["reps"] = "Enter reps from 1 to 1000"
        }

        // Rounded to the scale the server stores: two places for weight, one for RPE.
        var weightValue: Decimal?
        if !weight.trimmingCharacters(in: .whitespaces).isEmpty {
            weightValue = DecimalInput.parse(weight)?.rounded(scale: 2)
            if weightValue == nil || !Self.weight.contains(weightValue!) {
                problems["weight"] = "Enter a weight up to 9999.99"
            }
        }

        var rpeValue: Decimal?
        if !rpe.trimmingCharacters(in: .whitespaces).isEmpty {
            rpeValue = DecimalInput.parse(rpe)?.rounded(scale: 1)
            if rpeValue == nil || !Self.rpe.contains(rpeValue!) {
                problems["rpe"] = "Enter an RPE from 1 to 10"
            }
        }

        guard problems.isEmpty, let repsValue else { throw ValidationFailure(fields: problems) }
        return LoggedSet(reps: repsValue, weight: weightValue, rpe: rpeValue)
    }
}
