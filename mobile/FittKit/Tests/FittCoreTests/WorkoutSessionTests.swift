import Foundation
import Testing
@testable import FittCore

@Suite("Workout session")
struct WorkoutSessionTests {
    @Test("parses the server's instants with and without fractional seconds", arguments: [
        ("2026-11-16T05:20:32Z", 1_794_806_432.0),
        ("2026-11-16T05:20:32.5Z", 1_794_806_432.5),
        ("2026-11-16T05:20:32.742695Z", 1_794_806_432.742695),
        ("2026-11-16T05:20:32.742695123Z", 1_794_806_432.742695123),
    ])
    func parsesInstants(text: String, seconds: Double) throws {
        let date = try #require(ServerInstant.parse(text))

        #expect(abs(date.timeIntervalSince1970 - seconds) < 0.000_01)
    }

    @Test("rejects text that is not an instant", arguments: ["", "2026-11-16", "yesterday", "2026-11-16T05:20:32.Z"])
    func rejectsBadInstants(text: String) {
        #expect(ServerInstant.parse(text) == nil)
    }

    @Test("formats a date as whole seconds in UTC")
    func formatsInstant() {
        #expect(ServerInstant.format(Date(timeIntervalSince1970: 1_794_806_432.9)) == "2026-11-16T05:20:32Z")
    }

    private let sessionJSON = #"""
    {"id":12,"ownerUserId":7,"planId":5,"startedAt":"2026-11-16T05:20:32.742695Z","finishedAt":null,"notes":null,"exerciseEntries":[
      {"id":31,"orderIndex":1,"exercise":{"id":14,"name":"Bent Over Barbell Row","muscleGroup":"BACK","custom":false},"sets":[]},
      {"id":30,"orderIndex":0,"exercise":{"id":1,"name":"Barbell Bench Press","muscleGroup":"CHEST","custom":false},"sets":[
        {"id":102,"setNumber":2,"reps":8,"weight":102.50,"rpe":8.5,"completed":true},
        {"id":101,"setNumber":1,"reps":8,"weight":100.00,"rpe":null,"completed":true}
      ]}
    ]}
    """#

    private func session() throws -> WorkoutSession {
        try JSONDecoder().decode(WorkoutSession.self, from: Data(sessionJSON.utf8))
    }

    @Test("decodes a session in progress")
    func decodesSession() throws {
        let session = try session()

        #expect(session.id == 12)
        #expect(session.planId == 5)
        #expect(!session.isFinished)
        #expect(session.loggedSetCount == 2)
        #expect(session.exerciseEntries[1].sets[0] == SetEntry(
            id: 102, setNumber: 2, reps: 8, weight: Decimal(string: "102.5"), rpe: Decimal(string: "8.5"), completed: true
        ))
    }

    @Test("entries and sets are ordered by their index and set number")
    func orders() throws {
        let session = try session()

        #expect(session.orderedEntries.map(\.exercise.name) == ["Barbell Bench Press", "Bent Over Barbell Row"])
        #expect(session.orderedEntries[0].orderedSets.map(\.setNumber) == [1, 2])
    }

    @Test("a finish time marks the session finished")
    func decodesFinishedSession() throws {
        let json = #"{"id":12,"planId":null,"startedAt":"2026-11-16T05:20:32Z","finishedAt":"2026-11-16T06:10:00.1Z","exerciseEntries":[]}"#

        let session = try JSONDecoder().decode(WorkoutSession.self, from: Data(json.utf8))

        #expect(session.isFinished)
        #expect(session.planId == nil)
    }

    @Test("a start time that is not an instant fails to decode")
    func rejectsBadStartTime() {
        let json = #"{"id":12,"planId":null,"startedAt":"soon","finishedAt":null,"exerciseEntries":[]}"#

        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(WorkoutSession.self, from: Data(json.utf8))
        }
    }

    @Test("a new session encodes its start time and plan")
    func encodesNewSession() throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let body = NewWorkoutSession(startedAt: Date(timeIntervalSince1970: 1_794_806_432), planId: 5)

        #expect(String(decoding: try encoder.encode(body), as: UTF8.self) == #"{"planId":5,"startedAt":"2026-11-16T05:20:32Z"}"#)
    }

    // MARK: Set draft

    @Test("a full draft becomes the set to log")
    func fullDraft() throws {
        #expect(try SetDraft(weight: "102.5", reps: "8", rpe: "8.5").loggedSet()
            == LoggedSet(reps: 8, weight: Decimal(string: "102.5"), rpe: Decimal(string: "8.5")))
    }

    @Test("weight and RPE may be left empty")
    func bodyweightSet() throws {
        #expect(try SetDraft(reps: "12").loggedSet() == LoggedSet(reps: 12))
    }

    @Test("values are rounded to the scale the server stores")
    func rounds() throws {
        let set = try SetDraft(weight: "62.555", reps: "5", rpe: "7.25").loggedSet()

        #expect(set.weight == Decimal(string: "62.56"))
        #expect(set.rpe == Decimal(string: "7.3"))
    }

    @Test("reps are required and must be 1 to 1000", arguments: [("", false), ("0", false), ("1", true), ("1000", true), ("1001", false), ("8.5", false), ("abc", false)])
    func repsRule(text: String, accepted: Bool) {
        #expect(((try? SetDraft(reps: text).loggedSet()) != nil) == accepted)
    }

    @Test("every wrong field is reported")
    func reportsProblems() {
        #expect(throws: ValidationFailure(fields: [
            "reps": "Enter reps from 1 to 1000",
            "weight": "Enter a weight up to 9999.99",
            "rpe": "Enter an RPE from 1 to 10",
        ])) {
            try SetDraft(weight: "10000", reps: "", rpe: "11").loggedSet()
        }
    }

    @Test("a set with no weight or RPE leaves them out of the body")
    func encodesWithoutNulls() throws {
        let json = String(decoding: try JSONEncoder().encode(LoggedSet(reps: 12)), as: UTF8.self)

        #expect(json == #"{"reps":12}"#)
    }
}
