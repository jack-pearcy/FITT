import Foundation

/// Mirrors the backend `BodyWeightEntryResponse` record: the weight logged for one day.
public struct BodyWeightEntry: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let loggedAt: CalendarDate
    /// Stored to one decimal place. The API carries no unit with it.
    public let weight: Decimal

    public init(id: Int64, loggedAt: CalendarDate, weight: Decimal) {
        self.id = id
        self.loggedAt = loggedAt
        self.weight = weight
    }
}

/// Mirrors the backend `BodyWeightAverageResponse` record.
public struct BodyWeightAverage: Decodable, Equatable, Sendable {
    /// Zero when nothing was logged in the last seven days.
    public let average: Decimal

    public init(average: Decimal) {
        self.average = average
    }
}

/// The body of `POST /api/users/{id}/measurements/weight`. The server keeps one
/// entry per day, so posting a date that already has one replaces its weight.
public struct NewBodyWeight: Encodable, Equatable, Sendable {
    public let loggedAt: CalendarDate
    public let weight: Decimal

    public init(loggedAt: CalendarDate, weight: Decimal) {
        self.loggedAt = loggedAt
        self.weight = weight
    }
}

/// The log weight form as typed.
public struct BodyWeightDraft: Equatable, Sendable {
    public static let weight: ClosedRange<Decimal> = Decimal(string: "0.1")!...700

    public var date: CalendarDate
    public var weight = ""

    public init(date: CalendarDate, weight: String = "") {
        self.date = date
        self.weight = weight
    }

    public func entry(today: CalendarDate) throws(ValidationFailure) -> NewBodyWeight {
        var problems: [String: String] = [:]

        // Rounded to one place, the scale of the server's decimal(5,1) column.
        let value = DecimalInput.parse(weight)?.rounded(scale: 1)
        if value == nil || !Self.weight.contains(value!) {
            problems["weight"] = "Enter a weight from 0.1 to 700"
        }
        if date > today {
            problems["loggedAt"] = "The date cannot be in the future"
        }

        guard problems.isEmpty, let value else { throw ValidationFailure(fields: problems) }
        return NewBodyWeight(loggedAt: date, weight: value)
    }

    /// The entry already logged for this draft's date, which saving would replace.
    public func replaced(in entries: [BodyWeightEntry]) -> BodyWeightEntry? {
        entries.first { $0.loggedAt == date }
    }
}
