import Foundation

/// Mirrors the backend `BodyWeightEntryResponse` record: the weight logged for one day.
public struct BodyWeightEntry: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let loggedAt: CalendarDate
    /// Kilograms, stored to two decimal places.
    public let weight: Decimal

    public init(id: Int64, loggedAt: CalendarDate, weight: Decimal) {
        self.id = id
        self.loggedAt = loggedAt
        self.weight = weight
    }
}

/// Mirrors the backend `BodyWeightAverageResponse` record.
public struct BodyWeightAverage: Decodable, Equatable, Sendable {
    /// Kilograms. Zero when nothing was logged in the last seven days.
    public let average: Decimal

    public init(average: Decimal) {
        self.average = average
    }
}

/// The body of `POST /api/users/{id}/measurements/weight`. The server keeps one
/// entry per day, so posting a date that already has one replaces its weight.
public struct NewBodyWeight: Encodable, Equatable, Sendable {
    public let loggedAt: CalendarDate
    /// Kilograms.
    public let weight: Decimal

    public init(loggedAt: CalendarDate, weight: Decimal) {
        self.loggedAt = loggedAt
        self.weight = weight
    }
}

/// The log weight form as typed. The weight is in the unit the user works in.
public struct BodyWeightDraft: Equatable, Sendable {
    /// The server's limits, in kilograms.
    public static let weight: ClosedRange<Decimal> = Decimal(string: "0.1")!...700

    public var date: CalendarDate
    public var weight = ""

    public init(date: CalendarDate, weight: String = "") {
        self.date = date
        self.weight = weight
    }

    /// The entry to send. The weight is converted to kilograms, the unit the server stores.
    public func entry(today: CalendarDate, units: UnitsPreference = .metric) throws(ValidationFailure) -> NewBodyWeight {
        var problems: [String: String] = [:]

        // A scale reads to one place, so that is what is kept of the typed number.
        let value = DecimalInput.parse(weight).map { units.kilograms(typed: $0.rounded(scale: 1)) }
        if value == nil || !Self.weight.contains(value!) {
            problems["weight"] = switch units {
            case .metric: "Enter a weight from 0.1 to 700"
            case .imperial: "Enter a weight from 0.3 to 1543"
            }
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
