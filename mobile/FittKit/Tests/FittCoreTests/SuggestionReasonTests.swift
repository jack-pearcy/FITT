import Foundation
import Testing
@testable import FittCore

@Suite("Suggestion reasons")
struct SuggestionReasonTests {
    private func decode(_ json: String) throws -> ProgressionSuggestion {
        try JSONDecoder().decode(ProgressionSuggestion.self, from: Data(json.utf8))
    }

    private func suggestion(
        _ code: SuggestionReason?,
        weight: String,
        reps: Int? = nil,
        previous: String? = "100.0",
        rpe: String? = nil,
        week: Int? = nil,
        reasoning: String = "Server text with 102.5 in kilograms."
    ) -> ProgressionSuggestion {
        ProgressionSuggestion(
            exerciseId: 1, modelUsed: .doubleProgression, suggestedWeight: Decimal(string: weight)!,
            suggestedReps: reps, suggestedSets: 3, reasoning: reasoning,
            reasonCode: code, previousWeightKg: previous.flatMap { Decimal(string: $0) },
            targetReps: reps, averageRpe: rpe.flatMap { Decimal(string: $0) }, weekInCycle: week)
    }

    @Test("a response from before the structured fields still decodes, with them empty")
    func olderResponseDecodes() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"DOUBLE_PROGRESSION","suggestedWeight":"102.5","suggestedReps":8,"suggestedSets":3,"reasoning":"Keep going."}"#)

        #expect(suggestion.reasonCode == nil)
        #expect(suggestion.previousWeightKg == nil)
        #expect(suggestion.targetReps == nil)
        #expect(suggestion.averageRpe == nil)
        #expect(suggestion.weekInCycle == nil)
        #expect(suggestion.reasonText(units: .imperial) == "Keep going.")
    }

    @Test("the structured fields decode, with the decimals read from their text")
    func structuredFieldsDecode() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"RPE_AUTOREGULATION","suggestedWeight":"102.5","suggestedReps":8,"suggestedSets":3,"reasoning":"Average RPE was 6.5.","reasonCode":"RPE_LOW","previousWeightKg":"100.00","targetReps":8,"averageRpe":"6.5","weekInCycle":null}"#)

        #expect(suggestion.reasonCode == .rpeLow)
        #expect(suggestion.previousWeightKg == 100)
        #expect(suggestion.targetReps == 8)
        #expect(suggestion.averageRpe == Decimal(string: "6.5"))
        #expect(suggestion.weekInCycle == nil)
    }

    @Test("a linear suggestion carries its week of the cycle")
    func weekDecodes() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"LINEAR_PERIODIZATION","suggestedWeight":"60.0","suggestedReps":12,"suggestedSets":3,"reasoning":"Deload week.","reasonCode":"DELOAD","previousWeightKg":"100.0","targetReps":12,"averageRpe":null,"weekInCycle":4}"#)

        #expect(suggestion.reasonCode == .deload)
        #expect(suggestion.weekInCycle == 4)
    }

    @Test("a reason code this app does not know decodes, and the server's text is shown")
    func unknownCodeFallsBack() throws {
        let suggestion = try decode(#"{"exerciseId":1,"modelUsed":"DOUBLE_PROGRESSION","suggestedWeight":"102.5","suggestedReps":8,"suggestedSets":3,"reasoning":"A reason from a newer server.","reasonCode":"SOMETHING_NEW","previousWeightKg":"100.0","targetReps":8}"#)

        #expect(suggestion.reasonCode == nil)
        #expect(suggestion.reasonText(units: .imperial) == "A reason from a newer server.")
        #expect(suggestion.reasonText(units: .metric) == "A reason from a newer server.")
    }

    @Test("every reason code the server can send is known")
    func everyServerCodeIsKnown() {
        // The values of the backend SuggestionReason enum, in its order.
        let server = ["NO_HISTORY", "NO_SETS", "NO_RPE_DATA", "INCREASE_WEIGHT", "ADD_REPS", "RPE_LOW", "RPE_HIGH", "HOLD", "WEEK_STEP", "DELOAD"]

        #expect(SuggestionReason.allCases.map(\.rawValue) == server)
    }

    @Test("each reason reads in pounds for a user who works in pounds")
    func reasonsInPounds() {
        let texts: [SuggestionReason: String] = [
            .noHistory: suggestion(.noHistory, weight: "0", reps: 8, previous: nil).reasonText(units: .imperial),
            .noSets: suggestion(.noSets, weight: "0", reps: 8, previous: nil).reasonText(units: .imperial),
            .noRpeData: suggestion(.noRpeData, weight: "0", reps: 8, previous: nil).reasonText(units: .imperial),
            .increaseWeight: suggestion(.increaseWeight, weight: "102.5", reps: 8).reasonText(units: .imperial),
            .addReps: suggestion(.addReps, weight: "100.0", reps: 12).reasonText(units: .imperial),
            .rpeLow: suggestion(.rpeLow, weight: "102.5", reps: 8, rpe: "6.5").reasonText(units: .imperial),
            .rpeHigh: suggestion(.rpeHigh, weight: "97.5", reps: 8, rpe: "9.5").reasonText(units: .imperial),
            .hold: suggestion(.hold, weight: "100.0", reps: 8, rpe: "7.5").reasonText(units: .imperial),
            .weekStep: suggestion(.weekStep, weight: "102.5", reps: 8, week: 2).reasonText(units: .imperial),
            .deload: suggestion(.deload, weight: "60.0", reps: 12, week: 4).reasonText(units: .imperial),
        ]

        #expect(texts == [
            .noHistory: "No history for this exercise yet. Start with a weight you can control for 8 reps.",
            .noSets: "Your last workout has no set with reps and a weight for this exercise.",
            .noRpeData: "Your last workout has no set with an RPE for this exercise. Log RPE to get a weight.",
            .increaseWeight: "You reached the top of the rep range on every set. Go up from 220.5 lb to 226 lb and start again at 8 reps.",
            .addReps: "Stay at 220.5 lb and work up to 12 reps on every set before adding weight.",
            .rpeLow: "Your average RPE of 6.5 was below the target zone. Go up from 220.5 lb to 226 lb.",
            .rpeHigh: "Your average RPE of 9.5 was above the target zone. Drop from 220.5 lb to 215 lb.",
            .hold: "Your average RPE of 7.5 was inside the target zone. Stay at 220.5 lb.",
            .weekStep: "Week 2 of the cycle. Go up from 220.5 lb to 226 lb for 8 reps.",
            .deload: "Deload week. Drop from 220.5 lb to 132.3 lb for 12 reps to recover before the next cycle.",
        ])
        #expect(texts.count == SuggestionReason.allCases.count)
    }

    @Test("no reason written on the device carries the server's text", arguments: SuggestionReason.allCases)
    func knownCodesNeverFallBack(code: SuggestionReason) {
        let text = suggestion(code, weight: "102.5", reps: 8, rpe: "7.5", week: 2).reasonText(units: .imperial)

        #expect(!text.contains("Server text"))
        #expect(!text.contains("kg"))
    }

    @Test("the same reasons read in kilograms for a metric user")
    func reasonsInKilograms() {
        #expect(suggestion(.increaseWeight, weight: "102.5", reps: 8).reasonText(units: .metric)
            == "You reached the top of the rep range on every set. Go up from 100 kg to 102.5 kg and start again at 8 reps.")
        #expect(suggestion(.deload, weight: "60.0", reps: 12, week: 4).reasonText(units: .metric)
            == "Deload week. Drop from 100 kg to 60 kg for 12 reps to recover before the next cycle.")
    }

    @Test("a metric reason names the weights the server sent, to the hundredth")
    func kilogramsAreNotRounded() {
        #expect(suggestion(.increaseWeight, weight: "63.73", reps: 8, previous: "61.23").reasonText(units: .metric)
            == "You reached the top of the rep range on every set. Go up from 61.23 kg to 63.73 kg and start again at 8 reps.")
        #expect(suggestion(.increaseWeight, weight: "63.75", reps: 8, previous: "61.25").reasonText(units: .metric)
            == "You reached the top of the rep range on every set. Go up from 61.25 kg to 63.75 kg and start again at 8 reps.")
        // The same weights in pounds still read to one decimal place.
        #expect(suggestion(.rpeLow, weight: "63.73", previous: "61.23").reasonText(units: .imperial)
            == "Your average RPE was below the target zone. Go up from 135 lb to 140.5 lb.")
    }

    func missingNumbers() {
        #expect(suggestion(.rpeLow, weight: "102.5", previous: nil).reasonText(units: .imperial)
            == "Your average RPE was below the target zone. Go up to 226 lb.")
        #expect(suggestion(.weekStep, weight: "102.5", previous: nil).reasonText(units: .imperial)
            == "Go up to 226 lb.")
        #expect(suggestion(.addReps, weight: "100.0").reasonText(units: .imperial)
            == "Stay at 220.5 lb and add reps on every set before adding weight.")
        #expect(suggestion(.noHistory, weight: "0", previous: nil).reasonText(units: .imperial)
            == "No history for this exercise yet. Start with a weight you can control.")
    }

    @Test("a weight reason with no weight to aim for shows the server's text")
    func noTargetFallsBack() {
        #expect(suggestion(.addReps, weight: "0", reps: 12, reasoning: "Keep 0 and work toward 12 reps.").reasonText(units: .imperial)
            == "Keep 0 and work toward 12 reps.")
    }

    // MARK: What the workout screen shows

    @Test("a reason with a code is shown in the user's unit, never as the server's text")
    func shownReasonUsesTheFormatter() {
        let suggestion = suggestion(
            .increaseWeight, weight: "102.5", reps: 8,
            reasoning: "Increase weight to 102.5 and reset to 8 reps")

        #expect(suggestion.shownReason(units: .imperial)
            == "You reached the top of the rep range on every set. Go up from 220.5 lb to 226 lb and start again at 8 reps.")
        #expect(suggestion.shownReason(units: .metric)
            == "You reached the top of the rep range on every set. Go up from 100 kg to 102.5 kg and start again at 8 reps.")
    }

    @Test("every reason with a code is shown to a user who works in pounds", arguments: SuggestionReason.allCases)
    func codedReasonsAreShownInPounds(code: SuggestionReason) {
        let suggestion = suggestion(code, weight: "102.5", reps: 8, rpe: "7.5", week: 2)

        #expect(suggestion.shownReason(units: .imperial) == suggestion.reasonText(units: .imperial))
    }

    @Test("the server's text in kilograms is hidden from a user in pounds who has a target")
    func uncodedReasonHiddenInPounds() {
        let older = suggestion(nil, weight: "102.5", reps: 8, reasoning: "Increase weight to 102.5 and reset to 8 reps")

        #expect(older.shownReason(units: .imperial) == nil)
        #expect(older.shownReason(units: .metric) == "Increase weight to 102.5 and reset to 8 reps")
    }

    @Test("the server's text is shown in pounds when there is no target for it to clash with")
    func uncodedReasonShownWithoutTarget() {
        let older = suggestion(nil, weight: "0", previous: nil, reasoning: "No history yet.")

        #expect(older.shownReason(units: .imperial) == "No history yet.")
        #expect(older.shownReason(units: .metric) == "No history yet.")
    }
}
