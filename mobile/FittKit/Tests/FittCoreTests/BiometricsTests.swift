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

    // MARK: Reading and editing saved biometrics

    private let saved = Biometrics(
        heightCm: Decimal(string: "180.34")!,
        weightKg: Decimal(string: "81.65")!,
        dateOfBirth: CalendarDate(year: 1990, month: 5, day: 17),
        biologicalSex: .male,
        activityLevel: .active,
        unitsPreference: .imperial
    )

    @Test("the server's biometrics response decodes, and its updated time is not kept")
    func decodesResponse() throws {
        let json = #"{"heightCm":180.34,"weightKg":81.65,"dateOfBirth":"1990-05-17","biologicalSex":"MALE","activityLevel":"ACTIVE","unitsPreference":"IMPERIAL","updatedAt":"2026-11-20T10:15:30Z"}"#

        #expect(try JSONDecoder().decode(Biometrics.self, from: Data(json.utf8)) == saved)
    }

    @Test("changing the units of saved biometrics changes nothing else")
    func withUnits() {
        let metric = saved.with(units: .metric)

        #expect(metric.unitsPreference == .metric)
        #expect(metric.with(units: .imperial) == saved)
    }

    @Test("a draft made from imperial biometrics shows feet, inches and pounds, and saves back the same")
    func imperialDraftFromSaved() throws {
        let draft = BiometricsDraft(biometrics: saved)

        #expect(draft.units == .imperial)
        #expect(draft.feet == "5")
        #expect(draft.inches == "11")
        #expect(draft.weight == "180")
        #expect(draft.dateOfBirth == saved.dateOfBirth)
        #expect(draft.sex == .male)
        #expect(draft.activity == .active)
        #expect(try draft.biometrics(today: today) == saved)
    }

    @Test("a draft made from metric biometrics shows centimeters and kilograms, and saves back the same")
    func metricDraftFromSaved() throws {
        let metric = saved.with(units: .metric)
        let draft = BiometricsDraft(biometrics: metric)

        #expect(draft.height == "180.34")
        #expect(draft.weight == "81.65")
        #expect(try draft.biometrics(today: today) == metric)
    }

    /// Typed in metric at onboarding, then shown in imperial after a units
    /// change in Settings: neither number is a whole inch or a whole pound.
    private let typedInMetric = Biometrics(
        heightCm: 180,
        weightKg: 82,
        dateOfBirth: CalendarDate(year: 1990, month: 5, day: 17),
        biologicalSex: .male,
        activityLevel: .active,
        unitsPreference: .imperial
    )

    @Test("measures typed in metric and left alone in an imperial form save back as stored")
    func untouchedImperialFieldsKeepTheStoredValues() throws {
        var draft = BiometricsDraft(biometrics: typedInMetric)
        #expect(draft.feet == "5")
        #expect(draft.inches == "10.9")
        #expect(draft.weight == "180.8")

        #expect(try draft.biometrics(today: today) == typedInMetric)

        // Changing a field that is not a measure still leaves both as stored.
        draft.activity = .sedentary
        let changed = try draft.biometrics(today: today)
        #expect(changed.heightCm == 180)
        #expect(changed.weightKg == 82)
        #expect(changed.activityLevel == .sedentary)
    }

    @Test("stored measures round trip through an imperial form untouched", arguments: [
        ("50", "20"), ("152.4", "45.36"), ("167.5", "61.23"), ("175.26", "70"), ("180.09", "82.01"), ("193.7", "99.99"), ("300", "700"),
    ])
    func imperialRoundTrip(heightCm: String, weightKg: String) throws {
        let stored = Biometrics(
            heightCm: Decimal(string: heightCm)!, weightKg: Decimal(string: weightKg)!,
            dateOfBirth: typedInMetric.dateOfBirth, biologicalSex: .female, activityLevel: .active,
            unitsPreference: .imperial)

        #expect(try BiometricsDraft(biometrics: stored).biometrics(today: today) == stored)
    }

    @Test("a height or weight the user retypes is the one saved, and the other stays as stored")
    func editedImperialFieldIsConverted() throws {
        var weightEdited = BiometricsDraft(biometrics: typedInMetric)
        weightEdited.weight = "185"
        let lighter = try weightEdited.biometrics(today: today)
        #expect(lighter.weightKg == Decimal(string: "83.91"))
        #expect(lighter.heightCm == 180)

        var heightEdited = BiometricsDraft(biometrics: typedInMetric)
        heightEdited.inches = "11"
        let taller = try heightEdited.biometrics(today: today)
        #expect(taller.heightCm == Decimal(string: "180.34"))
        #expect(taller.weightKg == 82)

        var feetEdited = BiometricsDraft(biometrics: typedInMetric)
        feetEdited.feet = "6"
        #expect(try feetEdited.biometrics(today: today).heightCm == Decimal(string: "210.57"))
    }

    @Test("stored measures are not carried into a form switched to the other units")
    func switchedUnitsReadTheTypedFields() throws {
        // The first save from the empty state shows the units control, so a
        // draft can change units. The fields then mean something else.
        var draft = BiometricsDraft(biometrics: typedInMetric)
        draft.units = .metric

        #expect(throws: ValidationFailure(fields: ["heightCm": "Enter a height between 50 and 300 cm"])) {
            try draft.biometrics(today: today)
        }

        draft.height = "175"
        let metric = try draft.biometrics(today: today)
        #expect(metric.heightCm == 175)
        // "180.8" now reads as kilograms.
        #expect(metric.weightKg == Decimal(string: "180.8"))
    }

    @Test("a stored measure outside the limits is still refused when left alone")
    func untouchedOutOfRangeIsRefused() {
        let tooShort = Biometrics(
            heightCm: 40, weightKg: 82, dateOfBirth: typedInMetric.dateOfBirth,
            biologicalSex: .male, activityLevel: .active, unitsPreference: .imperial)

        #expect(throws: ValidationFailure(fields: ["heightCm": "Enter a height between 1 ft 8 in and 9 ft 10 in"])) {
            try BiometricsDraft(biometrics: tooShort).biometrics(today: today)
        }
    }

    @Test("a height is shown in the user's unit, with inches always below twelve")
    func heightText() {
        #expect(UnitsPreference.metric.heightText(centimeters: Decimal(string: "180.34")!) == "180.34 cm")
        #expect(UnitsPreference.imperial.heightText(centimeters: Decimal(string: "180.34")!) == "5 ft 11 in")
        #expect(UnitsPreference.imperial.heightText(centimeters: Decimal(string: "182.8")!) == "6 ft 0 in")
        #expect(UnitsPreference.imperial.heightText(centimeters: 180) == "5 ft 10.9 in")
    }
}
