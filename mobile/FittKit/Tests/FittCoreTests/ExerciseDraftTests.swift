import Foundation
import Testing
@testable import FittCore

@Suite("Exercise draft")
struct ExerciseDraftTests {
    @Test("a draft becomes a request with the name trimmed")
    func buildsRequest() throws {
        let draft = ExerciseDraft(name: "  Landmine Press \n", muscleGroup: .shoulders)

        #expect(try draft.exercise() == NewExercise(name: "Landmine Press", muscleGroup: .shoulders))
    }

    @Test("the request is encoded with the server's field names and enum value")
    func encodes() throws {
        let data = try JSONEncoder().encode(ExerciseDraft(name: "Hip Thrust", muscleGroup: .glutes).exercise())

        let body = try #require(try JSONSerialization.jsonObject(with: data) as? [String: String])
        #expect(body == ["name": "Hip Thrust", "muscleGroup": "GLUTES"])
    }

    @Test("a name that is empty or only spaces is refused", arguments: ["", "   ", "\n"])
    func requiresName(name: String) {
        #expect(throws: ValidationFailure(fields: ["name": "Exercise name is required"])) {
            try ExerciseDraft(name: name).exercise()
        }
    }

    @Test("a name is allowed up to 100 characters and refused above that")
    func limitsNameLength() throws {
        let longest = String(repeating: "a", count: 100)

        #expect(try ExerciseDraft(name: longest).exercise().name == longest)
        #expect(throws: ValidationFailure(fields: ["name": "Exercise name must be 100 characters or fewer"])) {
            try ExerciseDraft(name: longest + "a").exercise()
        }
    }
}
