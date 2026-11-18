import Foundation

public enum GoalDirection: String, Codable, CaseIterable, Sendable {
    case bulk = "BULK"
    case cut = "CUT"
    case maintain = "MAINTAIN"
}

public enum CalorieGoalMode: String, Codable, CaseIterable, Sendable {
    /// The server derives the goal from maintenance calories and the direction.
    case auto = "AUTO"
    /// The user supplies the number.
    case manual = "MANUAL"
}

/// The shape of both the backend `SaveGoalsRequest` and `GoalsResponse` records.
/// On a save in auto mode `calorieGoal` is left out and the server fills it in.
public struct Goals: Codable, Equatable, Sendable {
    public let goalDirection: GoalDirection
    public let targetWeightKg: Decimal?
    public let timelineWeeks: Int?
    public let calorieGoalMode: CalorieGoalMode
    public let calorieGoal: Int?

    public init(
        goalDirection: GoalDirection,
        targetWeightKg: Decimal? = nil,
        timelineWeeks: Int? = nil,
        calorieGoalMode: CalorieGoalMode,
        calorieGoal: Int? = nil
    ) {
        self.goalDirection = goalDirection
        self.targetWeightKg = targetWeightKg
        self.timelineWeeks = timelineWeeks
        self.calorieGoalMode = calorieGoalMode
        self.calorieGoal = calorieGoal
    }
}

/// The goals form as typed. Target weight and timeline are optional: empty means not set.
public struct GoalsDraft: Equatable, Sendable {
    public static let timelineWeeks = 1...520
    public static let calorieGoal = 500...10_000

    public var direction: GoalDirection?
    /// Kilograms or pounds, following the units passed to `goals(units:)`.
    public var targetWeight = ""
    public var timelineWeeks = ""
    public var mode = CalorieGoalMode.auto
    public var calories = ""

    public init() {}

    /// A draft filled in from saved goals, for editing them. The target weight
    /// is shown in the units the user works in.
    public init(goals: Goals, units: UnitsPreference) {
        direction = goals.goalDirection
        if let kilograms = goals.targetWeightKg {
            let shown = units == .metric ? kilograms : UnitConversion.pounds(kilograms: kilograms)
            targetWeight = "\(shown)"
        }
        if let weeks = goals.timelineWeeks {
            timelineWeeks = String(weeks)
        }
        mode = goals.calorieGoalMode
        // In automatic mode the number is the server's, not something the user typed.
        if goals.calorieGoalMode == .manual, let calorieGoal = goals.calorieGoal {
            calories = String(calorieGoal)
        }
    }

    public func goals(units: UnitsPreference) throws(ValidationFailure) -> Goals {
        var problems: [String: String] = [:]

        if direction == nil {
            problems["goalDirection"] = "Choose one"
        }

        var targetWeightKg: Decimal?
        if !targetWeight.trimmingCharacters(in: .whitespaces).isEmpty {
            let typed = DecimalInput.parse(targetWeight)
            // Two places either way, the scale the server stores and the most it accepts.
            targetWeightKg = units == .metric ? typed?.rounded(scale: 2) : typed.map(UnitConversion.kilograms(pounds:))
            if targetWeightKg == nil || !BodyLimits.weightKg.contains(targetWeightKg!) {
                problems["targetWeightKg"] = units == .metric
                    ? "Enter a weight between 20 and 700 kg"
                    : "Enter a weight between 45 and 1540 lb"
            }
        }

        var weeks: Int?
        let weeksText = timelineWeeks.trimmingCharacters(in: .whitespaces)
        if !weeksText.isEmpty {
            weeks = Int(weeksText)
            if weeks == nil || !Self.timelineWeeks.contains(weeks!) {
                problems["timelineWeeks"] = "Enter between 1 and 520 weeks"
            }
        }

        // The server rejects manual mode with no number, and ignores a number in auto mode.
        var calorieGoal: Int?
        if mode == .manual {
            calorieGoal = Int(calories.trimmingCharacters(in: .whitespaces))
            if calorieGoal == nil || !Self.calorieGoal.contains(calorieGoal!) {
                problems["calorieGoal"] = "Enter between 500 and 10000 calories"
            }
        }

        guard problems.isEmpty, let direction else {
            throw ValidationFailure(fields: problems)
        }
        return Goals(
            goalDirection: direction,
            targetWeightKg: targetWeightKg,
            timelineWeeks: weeks,
            calorieGoalMode: mode,
            calorieGoal: calorieGoal
        )
    }
}
