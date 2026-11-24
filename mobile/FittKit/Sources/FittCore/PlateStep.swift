import Foundation

/// How far one tap moves a weight: the smallest pair of plates a gym has, 2.5 kg
/// in metric and 5 lb in imperial. It works on the number the user sees, never
/// on kilograms, so a pound user steps from 225 to 230 and not to 230.4.
public struct PlateStep: Equatable, Sendable {
    public let size: Decimal

    public init(units: UnitsPreference) {
        switch units {
        case .metric: size = Decimal(25) / 10
        case .imperial: size = 5
        }
    }

    /// The next plate above. A weight between plates goes to the plate just
    /// above it, so steps line up with real plates again.
    public func up(_ value: Decimal) -> Decimal {
        (plates(in: value, rounding: .down) + 1) * size
    }

    /// The next plate below, never below zero.
    public func down(_ value: Decimal) -> Decimal {
        max(0, (plates(in: value, rounding: .up) - 1) * size)
    }

    /// The nearest plate, halves going up.
    public func rounded(_ value: Decimal) -> Decimal {
        plates(in: value, rounding: .plain) * size
    }

    /// Steps typed text and gives back plain text with a dot, which the form
    /// reads back on any keyboard. Text that is not a number counts as zero,
    /// and the result stays inside what the form accepts.
    public func stepped(_ text: String, up goingUp: Bool) -> String {
        let value = DecimalInput.parse(text) ?? 0
        let next = goingUp ? up(value) : down(value)
        return Self.plain(min(next, SetDraft.weight.upperBound))
    }

    /// A weight as form text: "82.5", "80", never "1,000".
    static func plain(_ value: Decimal) -> String {
        value.description
    }

    /// How many steps fit in a value, as a whole number.
    private func plates(in value: Decimal, rounding mode: NSDecimalNumber.RoundingMode) -> Decimal {
        var quotient = value / size
        var whole = Decimal()
        NSDecimalRound(&whole, &quotient, 0, mode)
        return whole
    }
}

/// Reps go up and down by one, inside what the form accepts.
public enum RepStep {
    public static func stepped(_ text: String, up goingUp: Bool) -> String {
        let reps = SetDraft.reps
        guard let value = Int(text.trimmingCharacters(in: .whitespaces)) else {
            return String(reps.lowerBound)
        }
        let next = goingUp ? value + 1 : value - 1
        return String(min(max(next, reps.lowerBound), reps.upperBound))
    }
}
