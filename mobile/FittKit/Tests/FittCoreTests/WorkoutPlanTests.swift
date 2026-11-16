import Foundation
import Testing
@testable import FittCore

@Suite("Workout plans")
struct WorkoutPlanTests {
    private let planJSON = #"""
    {"id":3,"ownerUserId":7,"name":"Upper Lower","daysPerWeek":4,"splitType":"UPPER_LOWER","active":true,"days":[
      {"id":12,"orderIndex":1,"dayLabel":"Lower","exercises":[]},
      {"id":11,"orderIndex":0,"dayLabel":"Upper","exercises":[
        {"id":102,"orderIndex":1,"sets":3,"reps":10,"exercise":{"id":9,"name":"Barbell Row","muscleGroup":"BACK","custom":false}},
        {"id":101,"orderIndex":0,"sets":4,"reps":6,"exercise":{"id":5,"name":"Bench Press","muscleGroup":"CHEST","custom":false}}
      ]}
    ]}
    """#

    private func plan() throws -> WorkoutPlan {
        try JSONDecoder().decode(WorkoutPlan.self, from: Data(planJSON.utf8))
    }

    @Test("decodes the server's plan, ignoring fields the app does not use")
    func decodesPlan() throws {
        let plan = try plan()

        #expect(plan.id == 3)
        #expect(plan.name == "Upper Lower")
        #expect(plan.daysPerWeek == 4)
        #expect(plan.splitType == .upperLower)
        #expect(plan.active)
        #expect(plan.days.count == 2)
        #expect(plan.days[1].exercises[1] == PlanDayExercise(
            id: 101, orderIndex: 0, sets: 4, reps: 6,
            exercise: Exercise(id: 5, name: "Bench Press", muscleGroup: .chest, custom: false)
        ))
    }

    @Test("days and exercises are ordered by orderIndex, whatever order the server sent")
    func ordersByIndex() throws {
        let plan = try plan()

        #expect(plan.orderedDays.map(\.dayLabel) == ["Upper", "Lower"])
        #expect(plan.orderedDays[0].orderedExercises.map(\.exercise.name) == ["Bench Press", "Barbell Row"])
    }

    @Test("every split type and muscle group token the server can send decodes", arguments: [
        ("PPL", SplitType.pushPullLegs), ("UPPER_LOWER", .upperLower), ("FULL_BODY", .fullBody), ("CUSTOM", .custom),
    ])
    func decodesSplitTypes(token: String, expected: SplitType) throws {
        #expect(try JSONDecoder().decode([SplitType].self, from: Data(#"["\#(token)"]"#.utf8)) == [expected])
    }

    @Test("the muscle group tokens match the server's enum")
    func muscleGroupTokens() {
        #expect(MuscleGroup.allCases.map(\.rawValue) == ["CHEST", "BACK", "SHOULDERS", "ARMS", "LEGS", "GLUTES", "CORE"])
    }

    @Test("next lift with no active plan decodes its nulls")
    func decodesNextLiftWithoutPlan() throws {
        let json = #"{"hasActivePlan":false,"planId":null,"planName":null,"daysInRotation":0,"dayNumber":0,"weekInCycle":1,"deload":false,"alreadyTrainedToday":false,"day":null}"#

        #expect(try JSONDecoder().decode(NextLift.self, from: Data(json.utf8)) == NextLift(hasActivePlan: false))
    }

    @Test("next lift with a plan carries the day to train")
    func decodesNextLiftWithDay() throws {
        let json = #"{"hasActivePlan":true,"planId":3,"planName":"Upper Lower","daysInRotation":2,"dayNumber":2,"weekInCycle":4,"deload":true,"alreadyTrainedToday":true,"day":{"id":12,"orderIndex":1,"dayLabel":"Lower","exercises":[]}}"#

        #expect(try JSONDecoder().decode(NextLift.self, from: Data(json.utf8)) == NextLift(
            hasActivePlan: true, planId: 3, planName: "Upper Lower", daysInRotation: 2, dayNumber: 2,
            weekInCycle: 4, deload: true, alreadyTrainedToday: true,
            day: WorkoutPlanDay(id: 12, orderIndex: 1, dayLabel: "Lower", exercises: [])
        ))
    }
}
