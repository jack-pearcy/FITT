import Foundation

/// How the server works out what to lift next. Mirrors the backend
/// `ProgressionModel` enum.
public enum ProgressionModel: String, Codable, CaseIterable, Sendable {
    case doubleProgression = "DOUBLE_PROGRESSION"
    case rpeAutoregulation = "RPE_AUTOREGULATION"
    case linearPeriodization = "LINEAR_PERIODIZATION"
}

/// The body of `PUT /api/users/{id}/progression/preference`.
public struct ProgressionPreference: Encodable, Equatable, Sendable {
    public let progressionModel: ProgressionModel

    public init(progressionModel: ProgressionModel) {
        self.progressionModel = progressionModel
    }
}

/// Why the server made a suggestion. Mirrors the backend `SuggestionReason` enum.
public enum SuggestionReason: String, CaseIterable, Sendable {
    case noHistory = "NO_HISTORY"
    case noSets = "NO_SETS"
    case noRpeData = "NO_RPE_DATA"
    case increaseWeight = "INCREASE_WEIGHT"
    case addReps = "ADD_REPS"
    case rpeLow = "RPE_LOW"
    case rpeHigh = "RPE_HIGH"
    case hold = "HOLD"
    case weekStep = "WEEK_STEP"
    case deload = "DELOAD"
}

/// Mirrors the backend `ProgressionSuggestionResponse` record: what to do for
/// one exercise in the next session, and why.
public struct ProgressionSuggestion: Decodable, Equatable, Sendable {
    public let exerciseId: Int64
    public let modelUsed: ProgressionModel
    /// Zero when the server has nothing to base a weight on.
    public let suggestedWeight: Decimal
    public let suggestedReps: Int?
    public let suggestedSets: Int?
    /// The reason in English with weights in kilograms, as older apps show it.
    public let reasoning: String
    /// The same reason in a form `reasonText(units:)` can write in the user's
    /// unit. Nil from an older server, and for a code this app does not know.
    public let reasonCode: SuggestionReason?
    /// The weight the suggestion started from. Nil when the reason did not use one.
    public let previousWeightKg: Decimal?
    public let targetReps: Int?
    public let averageRpe: Decimal?
    /// The week of a linear periodization cycle, counted from 1.
    public let weekInCycle: Int?

    public init(
        exerciseId: Int64, modelUsed: ProgressionModel, suggestedWeight: Decimal, suggestedReps: Int?, suggestedSets: Int?, reasoning: String,
        reasonCode: SuggestionReason? = nil, previousWeightKg: Decimal? = nil, targetReps: Int? = nil, averageRpe: Decimal? = nil, weekInCycle: Int? = nil
    ) {
        self.exerciseId = exerciseId
        self.modelUsed = modelUsed
        self.suggestedWeight = suggestedWeight
        self.suggestedReps = suggestedReps
        self.suggestedSets = suggestedSets
        self.reasoning = reasoning
        self.reasonCode = reasonCode
        self.previousWeightKg = previousWeightKg
        self.targetReps = targetReps
        self.averageRpe = averageRpe
        self.weekInCycle = weekInCycle
    }

    /// True when there is a weight to aim for. Without history the server sends
    /// zero and only the reasoning is worth showing.
    public var hasTarget: Bool { suggestedWeight > 0 }

    private enum CodingKeys: String, CodingKey {
        case exerciseId, modelUsed, suggestedWeight, suggestedReps, suggestedSets, reasoning
        case reasonCode, previousWeightKg, targetReps, averageRpe, weekInCycle
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        exerciseId = try container.decode(Int64.self, forKey: .exerciseId)
        modelUsed = try container.decode(ProgressionModel.self, forKey: .modelUsed)
        suggestedReps = try container.decodeIfPresent(Int.self, forKey: .suggestedReps)
        suggestedSets = try container.decodeIfPresent(Int.self, forKey: .suggestedSets)
        reasoning = try container.decode(String.self, forKey: .reasoning)
        // The fields below are optional so a response from an older server
        // decodes, and read loosely so one this app cannot use, such as a
        // reason code added later, costs the reason text and not the suggestion.
        let posix = Locale(identifier: "en_US_POSIX")
        func optional<Value: Decodable>(_ key: CodingKeys) -> Value? {
            (try? container.decodeIfPresent(Value.self, forKey: key)) ?? nil
        }
        reasonCode = (optional(.reasonCode) as String?).flatMap(SuggestionReason.init(rawValue:))
        // Decimals arrive as text, the same way the suggested weight does.
        previousWeightKg = (optional(.previousWeightKg) as String?).flatMap { Decimal(string: $0, locale: posix) }
        averageRpe = (optional(.averageRpe) as String?).flatMap { Decimal(string: $0, locale: posix) }
        targetReps = optional(.targetReps)
        weekInCycle = optional(.weekInCycle)

        // The server sends the weight as text so no digits are lost on the way.
        let text = try container.decode(String.self, forKey: .suggestedWeight)
        guard let weight = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")), weight >= 0 else {
            throw DecodingError.dataCorruptedError(forKey: .suggestedWeight, in: container, debugDescription: "Not a weight: \(text)")
        }
        suggestedWeight = weight
    }
}
