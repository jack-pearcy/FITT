import Foundation
import Testing
@testable import FittCore

@Suite("Progression")
struct ProgressionTests {
    private func decode(_ json: String) throws -> ProgressionSuggestion {
        try JSONDecoder().decode(ProgressionSuggestion.self, from: Data(json.utf8))
    }

    @Test("a suggestion decodes, with the weight read exactly from its text")
    func decodesSuggestion() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"DOUBLE_PROGRESSION","suggestedWeight":"102.35","suggestedReps":8,"suggestedSets":3,"reasoning":"Keep going."}"#)

        #expect(suggestion == ProgressionSuggestion(
            exerciseId: 1, modelUsed: .doubleProgression, suggestedWeight: Decimal(string: "102.35")!,
            suggestedReps: 8, suggestedSets: 3, reasoning: "Keep going."))
        #expect(suggestion.hasTarget)
    }

    @Test("a suggestion with no history has a zero weight and no target")
    func noHistory() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"RPE_AUTOREGULATION","suggestedWeight":"0","suggestedReps":null,"suggestedSets":null,"reasoning":"No session history."}"#)

        #expect(suggestion.suggestedWeight == 0)
        #expect(suggestion.suggestedReps == nil)
        #expect(!suggestion.hasTarget)
    }

    @Test("a weight that is not a number, or is negative, fails to decode", arguments: ["heavy", "", "-5"])
    func rejectsBadWeight(weight: String) {
        #expect(throws: DecodingError.self) {
            try decode(#"{"exerciseId":1,"modelUsed":"DOUBLE_PROGRESSION","suggestedWeight":"\#(weight)","suggestedReps":8,"suggestedSets":3,"reasoning":""}"#)
        }
    }

    @Test("every model token the server can send decodes", arguments: ["DOUBLE_PROGRESSION", "RPE_AUTOREGULATION", "LINEAR_PERIODIZATION"])
    func decodesModels(token: String) throws {
        // The preference route answers with a bare JSON string.
        let model = try JSONDecoder().decode(ProgressionModel.self, from: Data("\"\(token)\"".utf8))

        #expect(model.rawValue == token)
        #expect(ProgressionModel.allCases.count == 3)
    }

    @Test("the preference body uses the server's field name and enum value")
    func encodesPreference() throws {
        let data = try JSONEncoder().encode(ProgressionPreference(progressionModel: .linearPeriodization))

        let body = try #require(try JSONSerialization.jsonObject(with: data) as? [String: String])
        #expect(body == ["progressionModel": "LINEAR_PERIODIZATION"])
    }
}
