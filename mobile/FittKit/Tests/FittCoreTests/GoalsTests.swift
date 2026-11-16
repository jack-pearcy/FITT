import Foundation
import Testing
@testable import FittCore

@Suite("Goals")
struct GoalsTests {
    @Test("the smallest valid draft is a direction in auto mode")
    func minimalDraft() throws {
        var draft = GoalsDraft()
        draft.direction = .cut

        #expect(try draft.goals(units: .metric) == Goals(goalDirection: .cut, calorieGoalMode: .auto))
    }

    @Test("a direction is required")
    func needsDirection() {
        #expect(throws: ValidationFailure(fields: ["goalDirection": "Choose one"])) {
            try GoalsDraft().goals(units: .metric)
        }
    }

    @Test("manual mode needs a calorie number in range", arguments: [("", false), ("abc", false), ("499", false), ("500", true), ("10000", true), ("10001", false)])
    func manualCalories(text: String, accepted: Bool) {
        var draft = GoalsDraft()
        draft.direction = .maintain
        draft.mode = .manual
        draft.calories = text

        #expect(((try? draft.goals(units: .metric)) != nil) == accepted)
    }

    @Test("a number typed for manual mode is not sent in auto mode")
    func autoDropsCalories() throws {
        var draft = GoalsDraft()
        draft.direction = .bulk
        draft.calories = "3000"

        #expect(try draft.goals(units: .metric).calorieGoal == nil)
    }

    @Test("a target weight in pounds is converted")
    func imperialTarget() throws {
        var draft = GoalsDraft()
        draft.direction = .cut
        draft.targetWeight = "180"
        draft.timelineWeeks = "12"

        let goals = try draft.goals(units: .imperial)

        #expect(goals.targetWeightKg == Decimal(string: "81.65"))
        #expect(goals.timelineWeeks == 12)
    }

    @Test("optional fields are checked only when filled in")
    func optionalFieldsOutOfRange() {
        var draft = GoalsDraft()
        draft.direction = .cut
        draft.targetWeight = "10"
        draft.timelineWeeks = "521"

        #expect(throws: ValidationFailure(fields: [
            "targetWeightKg": "Enter a weight between 20 and 700 kg",
            "timelineWeeks": "Enter between 1 and 520 weeks",
        ])) {
            try draft.goals(units: .metric)
        }
    }

    @Test("unset optional fields are left out of the request body")
    func encodesWithoutNulls() throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let json = String(decoding: try encoder.encode(Goals(goalDirection: .bulk, calorieGoalMode: .auto)), as: UTF8.self)

        #expect(json == #"{"calorieGoalMode":"AUTO","goalDirection":"BULK"}"#)
    }

    @Test("decodes the server's goals response")
    func decodes() throws {
        let json = #"{"goalDirection":"CUT","targetWeightKg":78.50,"timelineWeeks":null,"calorieGoalMode":"AUTO","calorieGoal":2104}"#

        #expect(try JSONDecoder().decode(Goals.self, from: Data(json.utf8)) == Goals(
            goalDirection: .cut,
            targetWeightKg: Decimal(string: "78.5")!,
            calorieGoalMode: .auto,
            calorieGoal: 2104
        ))
    }
}
