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

/// Mirrors the backend `ProgressionSuggestionResponse` record: what to do for
/// one exercise in the next session, and why.
public struct ProgressionSuggestion: Decodable, Equatable, Sendable {
    public let exerciseId: Int64
    public let modelUsed: ProgressionModel
    /// Zero when the server has nothing to base a weight on.
    public let suggestedWeight: Decimal
    public let suggestedReps: Int?
    public let suggestedSets: Int?
    public let reasoning: String

    public init(exerciseId: Int64, modelUsed: ProgressionModel, suggestedWeight: Decimal, suggestedReps: Int?, suggestedSets: Int?, reasoning: String) {
        self.exerciseId = exerciseId
        self.modelUsed = modelUsed
        self.suggestedWeight = suggestedWeight
        self.suggestedReps = suggestedReps
        self.suggestedSets = suggestedSets
        self.reasoning = reasoning
    }

    /// True when there is a weight to aim for. Without history the server sends
    /// zero and only the reasoning is worth showing.
    public var hasTarget: Bool { suggestedWeight > 0 }

    private enum CodingKeys: String, CodingKey {
        case exerciseId, modelUsed, suggestedWeight, suggestedReps, suggestedSets, reasoning
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        exerciseId = try container.decode(Int64.self, forKey: .exerciseId)
        modelUsed = try container.decode(ProgressionModel.self, forKey: .modelUsed)
        suggestedReps = try container.decodeIfPresent(Int.self, forKey: .suggestedReps)
        suggestedSets = try container.decodeIfPresent(Int.self, forKey: .suggestedSets)
        reasoning = try container.decode(String.self, forKey: .reasoning)

        // The server sends the weight as text so no digits are lost on the way.
        let text = try container.decode(String.self, forKey: .suggestedWeight)
        guard let weight = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")), weight >= 0 else {
            throw DecodingError.dataCorruptedError(forKey: .suggestedWeight, in: container, debugDescription: "Not a weight: \(text)")
        }
        suggestedWeight = weight
    }
}
