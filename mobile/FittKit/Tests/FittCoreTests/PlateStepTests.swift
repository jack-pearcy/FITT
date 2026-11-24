import FittCore
import Foundation
import Testing

@Suite("PlateStep")
struct PlateStepTests {
    private func decimal(_ text: String) -> Decimal {
        Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) ?? -1
    }

    @Test("a metric step is 2.5 kg and an imperial one 5 lb")
    func sizes() {
        #expect(PlateStep(units: .metric).size == decimal("2.5"))
        #expect(PlateStep(units: .imperial).size == 5)
    }

    @Test("a weight on a plate moves by one step each way")
    func stepsFromAPlate() {
        let kg = PlateStep(units: .metric)
        #expect(kg.up(80) == decimal("82.5"))
        #expect(kg.down(80) == decimal("77.5"))
        let lb = PlateStep(units: .imperial)
        #expect(lb.up(225) == 230)
        #expect(lb.down(225) == 220)
    }

    @Test("a weight between plates moves to the next plate in that direction")
    func stepsFromBetweenPlates() {
        let kg = PlateStep(units: .metric)
        #expect(kg.up(81) == decimal("82.5"))
        #expect(kg.down(81) == 80)
        let lb = PlateStep(units: .imperial)
        #expect(lb.up(decimal("225.4")) == 230)
        #expect(lb.down(decimal("225.4")) == 225)
    }

    @Test("stepping down never goes below zero")
    func zeroFloor() {
        let kg = PlateStep(units: .metric)
        #expect(kg.down(0) == 0)
        #expect(kg.down(1) == 0)
        #expect(kg.down(decimal("2.5")) == 0)
        let lb = PlateStep(units: .imperial)
        #expect(lb.down(0) == 0)
        #expect(lb.down(3) == 0)
    }

    @Test("rounding goes to the nearest plate, halves up")
    func rounding() {
        let kg = PlateStep(units: .metric)
        #expect(kg.rounded(81) == 80)
        #expect(kg.rounded(decimal("101.3")) == decimal("102.5"))
        #expect(kg.rounded(decimal("101.25")) == decimal("102.5"))
        let lb = PlateStep(units: .imperial)
        #expect(lb.rounded(decimal("220.5")) == 220)
        #expect(lb.rounded(decimal("222.5")) == 225)
    }

    @Test("typed text steps as a number and comes back as plain text")
    func stepsText() {
        let kg = PlateStep(units: .metric)
        #expect(kg.stepped("80", up: true) == "82.5")
        #expect(kg.stepped("82,5", up: false) == "80")
        #expect(kg.stepped("", up: true) == "2.5")
        #expect(kg.stepped("", up: false) == "0")
        #expect(kg.stepped("abc", up: true) == "2.5")
        let lb = PlateStep(units: .imperial)
        #expect(lb.stepped("1000", up: true) == "1005")
    }

    @Test("a weight never steps past the largest the form accepts")
    func ceiling() {
        let kg = PlateStep(units: .metric)
        #expect(kg.stepped("9999", up: true) == "9999.99")
    }

    @Test("reps step by one, from one up to a thousand")
    func repsStep() {
        #expect(RepStep.stepped("8", up: true) == "9")
        #expect(RepStep.stepped("8", up: false) == "7")
        #expect(RepStep.stepped("1", up: false) == "1")
        #expect(RepStep.stepped("", up: true) == "1")
        #expect(RepStep.stepped("", up: false) == "1")
        #expect(RepStep.stepped("1000", up: true) == "1000")
    }
}
