import Foundation
import Testing
@testable import FittCore

@Suite("Measurement")
struct MeasurementTests {
    @Test("parses plain numbers, with a dot or a comma", arguments: [
        ("180", "180"), ("82.5", "82.5"), ("82,5", "82.5"), (" 70 ", "70"), (".5", "0.5"), ("5.", "5"),
    ])
    func parses(text: String, expected: String) {
        #expect(DecimalInput.parse(text) == Decimal(string: expected))
    }

    @Test("rejects anything that is not a plain number", arguments: ["", " ", ".", "12abc", "-5", "1.2.3", "1e3", "١٢"])
    func rejects(text: String) {
        #expect(DecimalInput.parse(text) == nil)
    }

    @Test("converts feet and inches to centimeters")
    func convertsHeight() {
        #expect(UnitConversion.centimeters(feet: 5, inches: 10) == Decimal(string: "177.8"))
        #expect(UnitConversion.centimeters(feet: 6, inches: 0) == Decimal(string: "182.88"))
        #expect(UnitConversion.centimeters(feet: 5, inches: Decimal(string: "9.5")!) == Decimal(string: "176.53"))
    }

    @Test("converts pounds to kilograms, rounded to two places")
    func convertsWeight() {
        #expect(UnitConversion.kilograms(pounds: 180) == Decimal(string: "81.65"))
        #expect(UnitConversion.kilograms(pounds: 100) == Decimal(string: "45.36"))
    }

    @Test("converts kilograms to pounds, rounded to one place")
    func convertsToPounds() {
        #expect(UnitConversion.pounds(kilograms: Decimal(string: "81.65")!) == 180)
        #expect(UnitConversion.pounds(kilograms: 100) == Decimal(string: "220.5"))
    }

    @Test("every whole number of pounds survives the trip to kilograms and back")
    func poundsRoundTrip() {
        for pounds in 45...1540 {
            let kilograms = UnitConversion.kilograms(pounds: Decimal(pounds))
            #expect(UnitConversion.pounds(kilograms: kilograms) == Decimal(pounds), "\(pounds) lb")
        }
    }
}
