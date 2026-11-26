import Foundation

public enum BiologicalSex: String, Codable, CaseIterable, Sendable {
    case male = "MALE"
    case female = "FEMALE"
}

public enum ActivityLevel: String, Codable, CaseIterable, Sendable {
    case sedentary = "SEDENTARY"
    case lightlyActive = "LIGHTLY_ACTIVE"
    case moderatelyActive = "MODERATELY_ACTIVE"
    case active = "ACTIVE"
    case veryActive = "VERY_ACTIVE"
}

public enum UnitsPreference: String, Codable, CaseIterable, Sendable {
    case metric = "METRIC"
    case imperial = "IMPERIAL"
}

/// Mirrors the backend `SaveBiometricsRequest` record, and reads the
/// `BiometricsResponse` record, which has the same fields plus an updated time
/// the app does not use. Always metric; the units preference only records how
/// the user wants values shown.
public struct Biometrics: Codable, Equatable, Sendable {
    public let heightCm: Decimal
    public let weightKg: Decimal
    public let dateOfBirth: CalendarDate
    public let biologicalSex: BiologicalSex
    public let activityLevel: ActivityLevel
    public let unitsPreference: UnitsPreference

    public init(
        heightCm: Decimal,
        weightKg: Decimal,
        dateOfBirth: CalendarDate,
        biologicalSex: BiologicalSex,
        activityLevel: ActivityLevel,
        unitsPreference: UnitsPreference
    ) {
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.dateOfBirth = dateOfBirth
        self.biologicalSex = biologicalSex
        self.activityLevel = activityLevel
        self.unitsPreference = unitsPreference
    }

    /// The same measures with another units preference.
    public func with(units: UnitsPreference) -> Biometrics {
        Biometrics(
            heightCm: heightCm, weightKg: weightKg, dateOfBirth: dateOfBirth,
            biologicalSex: biologicalSex, activityLevel: activityLevel, unitsPreference: units)
    }
}

/// Mirrors the backend `TdeeResponse` record: maintenance calories per day.
public struct Tdee: Decodable, Equatable, Sendable {
    public let tdee: Int

    public init(tdee: Int) {
        self.tdee = tdee
    }
}

/// The server's limits on stored body measurements, shared by biometrics and goals.
public enum BodyLimits {
    public static let heightCm: ClosedRange<Decimal> = 50...300
    public static let weightKg: ClosedRange<Decimal> = 20...700
}

/// The biometrics form as typed. `biometrics(today:)` turns it into a request
/// or reports which fields are wrong, so the screen holds no rules of its own.
public struct BiometricsDraft: Equatable, Sendable {
    public var units = UnitsPreference.metric
    /// Centimeters, used when `units` is metric.
    public var height = ""
    /// Feet and inches, used when `units` is imperial.
    public var feet = ""
    public var inches = ""
    /// Kilograms or pounds, following `units`.
    public var weight = ""
    public var dateOfBirth = CalendarDate(year: 2000, month: 1, day: 1)
    public var sex: BiologicalSex?
    public var activity: ActivityLevel?

    public init() {}

    /// A draft filled in from saved biometrics, for editing them, in the units
    /// they were saved with.
    public init(biometrics: Biometrics) {
        units = biometrics.unitsPreference
        switch units {
        case .metric:
            height = "\(biometrics.heightCm)"
        case .imperial:
            let parts = UnitConversion.feetAndInches(centimeters: biometrics.heightCm)
            feet = String(parts.feet)
            inches = "\(parts.inches)"
        }
        weight = "\(units.shown(kilograms: biometrics.weightKg))"
        dateOfBirth = biometrics.dateOfBirth
        sex = biometrics.biologicalSex
        activity = biometrics.activityLevel
    }

    public func biometrics(today: CalendarDate) throws(ValidationFailure) -> Biometrics {
        var problems: [String: String] = [:]

        let heightCm = heightInCentimeters
        if heightCm == nil || !BodyLimits.heightCm.contains(heightCm!) {
            problems["heightCm"] = units == .metric
                ? "Enter a height between 50 and 300 cm"
                : "Enter a height between 1 ft 8 in and 9 ft 10 in"
        }
        let weightKg = weightInKilograms
        if weightKg == nil || !BodyLimits.weightKg.contains(weightKg!) {
            problems["weightKg"] = units == .metric
                ? "Enter a weight between 20 and 700 kg"
                : "Enter a weight between 45 and 1540 lb"
        }
        if dateOfBirth >= today {
            problems["dateOfBirth"] = "Date of birth must be in the past"
        }
        if sex == nil {
            problems["biologicalSex"] = "Choose one"
        }
        if activity == nil {
            problems["activityLevel"] = "Choose one"
        }

        guard problems.isEmpty, let heightCm, let weightKg, let sex, let activity else {
            throw ValidationFailure(fields: problems)
        }
        return Biometrics(
            heightCm: heightCm,
            weightKg: weightKg,
            dateOfBirth: dateOfBirth,
            biologicalSex: sex,
            activityLevel: activity,
            unitsPreference: units
        )
    }

    private var heightInCentimeters: Decimal? {
        switch units {
        case .metric:
            // Two places, the scale the server stores and the most it accepts.
            return DecimalInput.parse(height)?.rounded(scale: 2)
        case .imperial:
            // Inches may be left empty ("6 ft"), but must stay below a foot.
            guard let feet = Int(feet.trimmingCharacters(in: .whitespaces)), feet >= 0 else { return nil }
            let inchesText = inches.trimmingCharacters(in: .whitespaces)
            guard let inches = inchesText.isEmpty ? 0 : DecimalInput.parse(inchesText), inches < 12 else { return nil }
            return UnitConversion.centimeters(feet: feet, inches: inches)
        }
    }

    private var weightInKilograms: Decimal? {
        guard let value = DecimalInput.parse(weight) else { return nil }
        return units == .metric ? value.rounded(scale: 2) : UnitConversion.kilograms(pounds: value)
    }
}
