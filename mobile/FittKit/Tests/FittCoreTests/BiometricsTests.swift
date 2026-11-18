import Foundation
import Testing
@testable import FittCore

@Suite("Biometrics")
struct BiometricsTests {
    private let today = CalendarDate(year: 2026, month: 11, day: 15)

    private func validDraft() -> BiometricsDraft {
        var draft = BiometricsDraft()
        draft.height = "180"
        draft.weight = "82.5"
        draft.dateOfBirth = CalendarDate(year: 1994, month: 3, day: 7)
        draft.sex = .male
        draft.activity = .moderatelyActive
        return draft
    }

    @Test("a metric draft becomes the request as typed")
    func metricDraft() throws {
        #expect(try validDraft().biometrics(today: today) == Biometrics(
            heightCm: 180,
            weightKg: Decimal(string: "82.5")!,
            dateOfBirth: CalendarDate(year: 1994, month: 3, day: 7),
            biologicalSex: .male,
            activityLevel: .moderatelyActive,
            unitsPreference: .metric
        ))
    }

    @Test("an imperial draft is converted to metric and keeps the preference")
    func imperialDraft() throws {
        var draft = validDraft()
        draft.units = .imperial
        draft.feet = "5"
        draft.inches = "10"
        draft.weight = "180"

        let biometrics = try draft.biometrics(today: today)

        #expect(biometrics.heightCm == Decimal(string: "177.8"))
        #expect(biometrics.weightKg == Decimal(string: "81.65"))
        #expect(biometrics.unitsPreference == .imperial)
    }

    @Test("inches may be left empty")
    func feetOnly() throws {
        var draft = validDraft()
        draft.units = .imperial
        draft.feet = "6"
        draft.weight = "180"

        #expect(try draft.biometrics(today: today).heightCm == Decimal(string: "182.88"))
    }

    @Test("twelve or more inches is rejected")
    func tooManyInches() {
        var draft = validDraft()
        draft.units = .imperial
        draft.feet = "5"
        draft.inches = "12"
        draft.weight = "180"

        #expect(throws: ValidationFailure(fields: ["heightCm": "Enter a height between 1 ft 8 in and 9 ft 10 in"])) {
            try draft.biometrics(today: today)
        }
    }

    @Test("an empty draft reports every field")
    func emptyDraft() {
        var draft = BiometricsDraft()
        draft.dateOfBirth = today

        #expect(throws: ValidationFailure(fields: [
            "heightCm": "Enter a height between 50 and 300 cm",
            "weightKg": "Enter a weight between 20 and 700 kg",
            "dateOfBirth": "Date of birth must be in the past",
            "biologicalSex": "Choose one",
            "activityLevel": "Choose one",
        ])) {
            try draft.biometrics(today: today)
        }
    }

    @Test("the server's limits are inclusive", arguments: [("50", "20", true), ("300", "700", true), ("49.99", "20", false), ("50", "700.01", false)])
    func limits(height: String, weight: String, accepted: Bool) {
        var draft = validDraft()
        draft.height = height
        draft.weight = weight

        #expect(((try? draft.biometrics(today: today)) != nil) == accepted)
    }

    @Test("yesterday is an acceptable date of birth, today is not")
    func dateOfBirthMustBePast() {
        var draft = validDraft()
        draft.dateOfBirth = CalendarDate(year: 2026, month: 11, day: 14)
        #expect((try? draft.biometrics(today: today)) != nil)
        draft.dateOfBirth = today
        #expect((try? draft.biometrics(today: today)) == nil)
    }

    @Test("encodes the field names and enum tokens the server expects")
    func encodes() throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        var draft = validDraft()
        draft.activity = .lightlyActive
        let json = String(decoding: try encoder.encode(draft.biometrics(today: today)), as: UTF8.self)

        #expect(json == #"{"activityLevel":"LIGHTLY_ACTIVE","biologicalSex":"MALE","dateOfBirth":"1994-03-07","heightCm":180,"unitsPreference":"METRIC","weightKg":82.5}"#)
    }

    @Test("metric height and weight are rounded to the two places the server accepts")
    func metricRounding() throws {
        var draft = validDraft()
        draft.height = "180.456"
        draft.weight = "82.455"

        let biometrics = try draft.biometrics(today: today)

        #expect(biometrics.heightCm == Decimal(string: "180.46"))
        #expect(biometrics.weightKg == Decimal(string: "82.46"))
    }
}
