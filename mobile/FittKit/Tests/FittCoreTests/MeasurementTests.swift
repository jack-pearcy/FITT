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

    @Test("a metric user sees and sends kilograms unchanged")
    func metricWeights() {
        let units = UnitsPreference.metric
        #expect(units.weightLabel == "kg")
        #expect(units.shown(kilograms: Decimal(string: "102.5")!) == Decimal(string: "102.5"))
        #expect(units.kilograms(typed: Decimal(string: "102.5")!) == Decimal(string: "102.5"))
        #expect(units.bodyWeightText(kilograms: Decimal(string: "82.44")!) == "82.4 kg")
    }

    @Test("an imperial user sees pounds and sends kilograms")
    func imperialWeights() {
        let units = UnitsPreference.imperial
        #expect(units.weightLabel == "lb")
        #expect(units.shown(kilograms: Decimal(string: "102.06")!) == 225)
        #expect(units.kilograms(typed: 225) == Decimal(string: "102.06"))
        #expect(units.bodyWeightText(kilograms: Decimal(string: "81.83")!) == "180.4 lb")
    }

    @Test("every tenth of a pound survives the trip to kilograms and back")
    func tenthsOfPoundsRoundTrip() {
        let units = UnitsPreference.imperial
        for tenths in 3...15430 {
            let pounds = Decimal(tenths) / 10
            #expect(units.shown(kilograms: units.kilograms(typed: pounds)) == pounds, "\(pounds) lb")
        }
    }
}
