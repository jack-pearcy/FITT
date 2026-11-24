import FittCore
import Foundation
import Testing

/// Sessions built in code for the prefill and last time tests.
enum PrefillFixtures {
    static let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    static let squat = Exercise(id: 38, name: "Back Squat", muscleGroup: .legs, custom: false)

    static func set(_ number: Int, reps: Int?, weight: String?) -> SetEntry {
        SetEntry(
            id: Int64(1000 + number),
            setNumber: number,
            reps: reps,
            weight: weight.flatMap { Decimal(string: $0, locale: Locale(identifier: "en_US_POSIX")) },
            rpe: nil,
            completed: true)
    }

    static func session(
        id: Int64,
        day: Int,
        finished: Bool = true,
        entries: [(Exercise, [SetEntry])]
    ) -> WorkoutSession {
        let started = Date(timeIntervalSince1970: 1_790_000_000 + Double(day) * 86_400)
        return WorkoutSession(
            id: id,
            planId: 5,
            startedAt: started,
            finishedAt: finished ? started.addingTimeInterval(3600) : nil,
            exerciseEntries: entries.enumerated().map { index, item in
                ExerciseEntry(id: id * 100 + Int64(index), orderIndex: index, exercise: item.0, sets: item.1)
            })
    }

    static func suggestion(weight: String, reps: Int?) -> ProgressionSuggestion {
        ProgressionSuggestion(
            exerciseId: bench.id,
            modelUsed: .doubleProgression,
            suggestedWeight: Decimal(string: weight, locale: Locale(identifier: "en_US_POSIX")) ?? 0,
            suggestedReps: reps,
            suggestedSets: 3,
            reasoning: "Hit the top of the range, so the weight goes up.")
    }
}

@Suite("SetPrefill")
struct SetPrefillTests {
    private typealias F = PrefillFixtures

    private let history = [
        F.session(id: 10, day: 1, entries: [(F.bench, [F.set(1, reps: 8, weight: "75"), F.set(2, reps: 8, weight: "75")])]),
        F.session(id: 11, day: 3, entries: [
            (F.squat, [F.set(1, reps: 5, weight: "120")]),
            (F.bench, [F.set(1, reps: 8, weight: "80"), F.set(2, reps: 8, weight: "80"), F.set(3, reps: 7, weight: "77.5")]),
        ]),
    ]

    @Test("a set already logged today is repeated as it is")
    func repeatsToday() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id,
            loggedToday: [F.set(1, reps: 8, weight: "81"), F.set(2, reps: 6, weight: "81")],
            suggestion: F.suggestion(weight: "102.5", reps: 8),
            history: history,
            units: .metric)
        #expect(draft == SetDraft(weight: "81", reps: "6"))
    }

    @Test("a bodyweight set logged today repeats with the weight left empty")
    func repeatsBodyweight() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [F.set(1, reps: 12, weight: nil)],
            suggestion: nil, history: [], units: .metric)
        #expect(draft == SetDraft(weight: "", reps: "12"))
    }

    @Test("with nothing logged today, a suggestion with a target fills the set")
    func fromSuggestion() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: history, units: .metric)
        #expect(draft == SetDraft(weight: "102.5", reps: "8"))
    }

    @Test("a suggestion in pounds is rounded to a 5 lb plate")
    func fromSuggestionInPounds() {
        // 102.5 kg is 225.97 lb, which shows as 226.0 and rounds to 225.
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: [], units: .imperial)
        #expect(draft == SetDraft(weight: "225", reps: "8"))
    }

    @Test("a suggestion without reps fills only the weight")
    func suggestionWithoutReps() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "60", reps: nil), history: [], units: .metric)
        #expect(draft == SetDraft(weight: "60", reps: ""))
    }

    @Test("a suggestion of zero is no target, so last time fills the set")
    func zeroSuggestionFallsBack() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "0", reps: 8), history: history, units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8"))
    }

    @Test("without a suggestion the newest session's set with the same number fills the set")
    func fromLastSession() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history, units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8"))
    }

    @Test("history is read newest first whatever order it arrives in")
    func historyOrder() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history.reversed(), units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8"))
    }

    @Test("a set number past last time's sets takes its last set")
    func pastLastSessionSets() {
        // A set logged today would win, so the history branch is asked directly.
        #expect(SetPrefill.fromHistory(exerciseId: F.bench.id, setNumber: 5, history: history, units: .metric)
            == SetDraft(weight: "77.5", reps: "7"))
        #expect(SetPrefill.fromHistory(exerciseId: F.bench.id, setNumber: 2, history: history, units: .metric)
            == SetDraft(weight: "80", reps: "8"))
    }

    @Test("last time in pounds is rounded to a 5 lb plate")
    func fromLastSessionInPounds() {
        // 80 kg is 176.4 lb, which rounds to 175.
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history, units: .imperial)
        #expect(draft == SetDraft(weight: "175", reps: "8"))
    }

    @Test("an unfinished session and an exercise with no sets are not last time")
    func skipsUnfinishedAndEmpty() {
        let sessions = [
            F.session(id: 20, day: 9, finished: false, entries: [(F.bench, [F.set(1, reps: 3, weight: "200")])]),
            F.session(id: 21, day: 8, entries: [(F.bench, [])]),
            F.session(id: 22, day: 7, entries: [(F.bench, [F.set(1, reps: 10, weight: "70")])]),
        ]
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .metric)
        #expect(draft == SetDraft(weight: "70", reps: "10"))
    }

    @Test("with no suggestion and no history the set starts empty")
    func empty() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: [], units: .metric)
        #expect(draft == SetDraft())
        let other = SetPrefill.draft(
            exerciseId: 99, loggedToday: [], suggestion: nil, history: history, units: .metric)
        #expect(other == SetDraft())
    }

    @Test("a prefilled set passes the form's own checks")
    func prefillIsValid() throws {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history, units: .imperial)
        let logged = try draft.loggedSet(units: .imperial)
        #expect(logged.reps == 8)
        #expect(logged.weight == Decimal(string: "79.38", locale: Locale(identifier: "en_US_POSIX")))
    }
}
