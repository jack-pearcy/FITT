import Foundation
import Testing
@testable import FittCore

@Suite("Plan draft")
struct PlanDraftTests {
    private let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    private let row = Exercise(id: 14, name: "Bent Over Barbell Row", muscleGroup: .back, custom: false)
    private let squat = Exercise(id: 38, name: "Back Squat", muscleGroup: .legs, custom: false)

    @Test("a new draft starts with the days of its split", arguments: [
        (SplitType.pushPullLegs, ["Push", "Pull", "Legs"], 3),
        (.upperLower, ["Upper", "Lower"], 4),
        (.fullBody, ["Full Body"], 3),
        (.custom, ["Day 1"], 3),
    ])
    func startsFromSplit(split: SplitType, labels: [String], daysPerWeek: Int) {
        let draft = PlanDraft(splitType: split)

        #expect(draft.days.map(\.label) == labels)
        #expect(draft.daysPerWeek == daysPerWeek)
    }

    @Test("changing the split replaces days the user has not touched")
    func splitChangeReplacesUntouchedDays() {
        var draft = PlanDraft(splitType: .pushPullLegs)

        draft.setSplitType(.upperLower)

        #expect(draft.splitType == .upperLower)
        #expect(draft.days.map(\.label) == ["Upper", "Lower"])
        #expect(draft.daysPerWeek == 4)
    }

    @Test("changing the split keeps days that already have exercises")
    func splitChangeKeepsEditedDays() {
        var draft = PlanDraft(splitType: .pushPullLegs)
        draft.add(bench, toDay: draft.days[0].id)

        draft.setSplitType(.fullBody)

        #expect(draft.splitType == .fullBody)
        #expect(draft.days.map(\.label) == ["Push", "Pull", "Legs"])
        #expect(draft.days[0].slots.map(\.exercise) == [bench])
    }

    @Test("changing the split keeps a renamed day and a chosen frequency")
    func splitChangeKeepsRenamedDay() {
        var draft = PlanDraft(splitType: .upperLower)
        draft.days[0].label = "Heavy Upper"
        draft.daysPerWeek = 2

        draft.setSplitType(.custom)

        #expect(draft.days.map(\.label) == ["Heavy Upper", "Lower"])
        #expect(draft.daysPerWeek == 2)
    }

    @Test("days can be added up to fourteen and removed down to one")
    func dayLimits() {
        var draft = PlanDraft(splitType: .custom)
        #expect(!draft.canRemoveDay)
        draft.removeDay(draft.days[0].id)
        #expect(draft.days.count == 1)

        for _ in 0..<20 { draft.addDay() }

        #expect(draft.days.count == 14)
        #expect(!draft.canAddDay)
        #expect(draft.days[1].label == "Day 2")
        draft.removeDay(draft.days[13].id)
        #expect(draft.days.count == 13)
    }

    @Test("an added exercise starts at three sets of ten")
    func addsExerciseWithDefaults() {
        var draft = PlanDraft(splitType: .custom)

        draft.add(squat, toDay: draft.days[0].id)

        let slot = draft.days[0].slots[0]
        #expect(slot.exercise == squat)
        #expect(slot.sets == 3)
        #expect(slot.reps == 10)
    }

    @Test("a day stops accepting exercises at fifty")
    func exerciseLimit() {
        var draft = PlanDraft(splitType: .custom)

        for _ in 0..<60 { draft.add(squat, toDay: draft.days[0].id) }

        #expect(draft.days[0].slots.count == 50)
    }

    @Test("a complete draft becomes the request, with order taken from position")
    func buildsRequest() throws {
        var draft = PlanDraft(splitType: .upperLower)
        draft.name = "  Upper Lower  "
        draft.add(bench, toDay: draft.days[0].id)
        draft.add(row, toDay: draft.days[0].id)
        draft.days[0].slots[0].sets = 4
        draft.days[0].slots[0].reps = 6
        draft.add(squat, toDay: draft.days[1].id)

        let plan = try draft.plan()

        #expect(plan.name == "Upper Lower")
        #expect(plan.daysPerWeek == 4)
        #expect(plan.splitType == .upperLower)
        #expect(plan.days.map(\.orderIndex) == [0, 1])
        #expect(plan.days.map(\.dayLabel) == ["Upper", "Lower"])
        #expect(plan.days[0].exercises == [
            NewWorkoutPlan.Slot(exerciseId: 1, sets: 4, reps: 6),
            NewWorkoutPlan.Slot(exerciseId: 14, sets: 3, reps: 10),
        ])
    }

    @Test("a day with no exercises is allowed, as it is on the server")
    func allowsEmptyDay() throws {
        var draft = PlanDraft(splitType: .custom)
        draft.name = "Empty"

        #expect(try draft.plan().days[0].exercises.isEmpty)
    }

    @Test("a missing name and a blank day label are reported under the server's field names")
    func reportsProblems() {
        var draft = PlanDraft(splitType: .upperLower)
        draft.days[1].label = "   "

        #expect(throws: ValidationFailure(fields: [
            "name": "Plan name is required",
            "days[1].dayLabel": "Day label is required",
        ])) {
            try draft.plan()
        }
    }

    @Test("names and labels over the server's lengths are rejected")
    func lengthLimits() {
        var draft = PlanDraft(splitType: .custom)
        draft.name = String(repeating: "n", count: 101)
        draft.days[0].label = String(repeating: "d", count: 51)

        #expect(throws: ValidationFailure(fields: [
            "name": "Plan name must be 100 characters or fewer",
            "days[0].dayLabel": "Day label must be 50 characters or fewer",
        ])) {
            try draft.plan()
        }

        draft.name = String(repeating: "n", count: 100)
        draft.days[0].label = String(repeating: "d", count: 50)
        #expect((try? draft.plan()) != nil)
    }

    @Test("sets and reps outside the server's ranges are rejected", arguments: [(0, 10), (21, 10), (3, 0), (3, 201)])
    func setAndRepLimits(sets: Int, reps: Int) {
        var draft = PlanDraft(splitType: .custom)
        draft.name = "Plan"
        draft.add(squat, toDay: draft.days[0].id)
        draft.days[0].slots[0].sets = sets
        draft.days[0].slots[0].reps = reps

        #expect(throws: ValidationFailure(fields: ["days[0].exercises": "Sets must be 1 to 20 and reps 1 to 200"])) {
            try draft.plan()
        }
    }

    @Test("the request encodes the field names and tokens the server expects")
    func encodes() throws {
        var draft = PlanDraft(splitType: .fullBody)
        draft.name = "Full Body"
        draft.add(squat, toDay: draft.days[0].id)
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys

        let json = String(decoding: try encoder.encode(draft.plan()), as: UTF8.self)

        #expect(json == #"{"days":[{"dayLabel":"Full Body","exercises":[{"exerciseId":38,"reps":10,"sets":3}],"orderIndex":0}],"daysPerWeek":3,"name":"Full Body","splitType":"FULL_BODY"}"#)
    }

    // MARK: Exercise catalog

    @Test("exercises are grouped in the server's muscle group order and sorted by name")
    func groupsExercises() {
        let curl = Exercise(id: 30, name: "barbell curl", muscleGroup: .arms, custom: true)
        let incline = Exercise(id: 2, name: "Incline Barbell Bench Press", muscleGroup: .chest, custom: false)

        let groups = ExerciseCatalog.groups([squat, curl, incline, row, bench])

        #expect(groups.map(\.muscleGroup) == [.chest, .back, .arms, .legs])
        #expect(groups[0].exercises == [bench, incline])
    }

    @Test("search ignores case and accents and drops empty groups")
    func searchesExercises() {
        let accented = Exercise(id: 99, name: "Développé Couché", muscleGroup: .chest, custom: true)

        #expect(ExerciseCatalog.groups([bench, row, squat], matching: " BARBELL ").map(\.muscleGroup) == [.chest, .back])
        #expect(ExerciseCatalog.groups([accented, squat], matching: "developpe").map(\.exercises) == [[accented]])
        #expect(ExerciseCatalog.groups([bench, row, squat], matching: "zzz").isEmpty)
    }
}
