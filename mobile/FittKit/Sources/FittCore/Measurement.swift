import Foundation

/// Reads a number typed into a form. Unlike `Decimal(string:)`, which accepts
/// "12abc" as 12, this rejects anything that is not a plain non-negative number.
public enum DecimalInput {
    public static func parse(_ text: String) -> Decimal? {
        // A comma is the decimal separator on many keyboards.
        let trimmed = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        let parts = trimmed.split(separator: ".", omittingEmptySubsequences: false)
        guard (1...2).contains(parts.count),
              parts.allSatisfy({ $0.allSatisfy(\.isASCII) && $0.allSatisfy(\.isNumber) }),
              parts.contains(where: { !$0.isEmpty })
        else { return nil }
        return Decimal(string: trimmed, locale: Locale(identifier: "en_US_POSIX"))
    }
}

/// Imperial input to the metric values the API stores. Results are rounded to two
/// decimal places, the scale of the server's decimal(5,2) columns.
public enum UnitConversion {
    private static let centimetersPerInch = Decimal(string: "2.54")!
    private static let kilogramsPerPound = Decimal(string: "0.45359237")!

    public static func centimeters(feet: Int, inches: Decimal) -> Decimal {
        ((Decimal(feet) * 12 + inches) * centimetersPerInch).rounded(scale: 2)
    }

    public static func kilograms(pounds: Decimal) -> Decimal {
        (pounds * kilogramsPerPound).rounded(scale: 2)
    }

    /// For showing a stored weight to an imperial user. One decimal place is enough
    /// that a whole number of pounds typed earlier comes back as the same number.
    public static func pounds(kilograms: Decimal) -> Decimal {
        (kilograms / kilogramsPerPound).rounded(scale: 1)
    }
}

extension Decimal {
    /// Rounds half away from zero to `scale` decimal places.
    func rounded(scale: Int) -> Decimal {
        var input = self
        var output = Decimal()
        NSDecimalRound(&output, &input, scale, .plain)
        return output
    }
}

/// A form that failed its checks on the device. The keys are the request field
/// names, the same keys the server uses in a 400 field map.
public struct ValidationFailure: Error, Equatable, Sendable {
    public let fields: [String: String]

    public init(fields: [String: String]) {
        self.fields = fields
    }
}
