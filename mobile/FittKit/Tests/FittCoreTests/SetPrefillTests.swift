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

    private func kg(_ text: String) -> Decimal {
        Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) ?? -1
    }

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
        #expect(draft == SetDraft(weight: "81", reps: "6", prefillKilograms: kg("81")))
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
        #expect(draft == SetDraft(weight: "102.5", reps: "8", prefillKilograms: kg("102.5")))
    }

    @Test("a suggestion in pounds shows as the pounds the suggestion line reads")
    func fromSuggestionInPounds() {
        // 102.5 kg is 225.97 lb, which shows as 226.0 and is not moved to a plate.
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: [], units: .imperial)
        #expect(draft == SetDraft(weight: "226", reps: "8", prefillKilograms: kg("102.5")))
        // 175 lb kept from last time is 79.38 kg and comes back as 175.
        let kept = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "79.38", reps: 12), history: [], units: .imperial)
        #expect(kept == SetDraft(weight: "175", reps: "12", prefillKilograms: kg("79.38")))
    }

    @Test("a suggestion to keep a weight off the plate grid prefills that weight")
    func suggestionOffGrid() {
        for (suggested, shown) in [("24", "24"), ("12", "12"), ("6", "6"), ("1", "1")] {
            let draft = SetPrefill.draft(
                exerciseId: F.bench.id, loggedToday: [],
                suggestion: F.suggestion(weight: suggested, reps: 12), history: history, units: .metric)
            #expect(draft == SetDraft(weight: shown, reps: "12", prefillKilograms: kg(suggested)))
        }
    }

    @Test("a set logged today in pounds is converted, not rounded, and wins over the suggestion")
    func repeatsTodayInPounds() {
        // 175 lb is stored as 79.38 kg, which shows as 175.0 lb again.
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [F.set(1, reps: 8, weight: "79.38")],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: history, units: .imperial)
        #expect(draft == SetDraft(weight: "175", reps: "8", prefillKilograms: kg("79.38")))
    }

    @Test("a suggestion without reps fills only the weight")
    func suggestionWithoutReps() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "60", reps: nil), history: [], units: .metric)
        #expect(draft == SetDraft(weight: "60", reps: "", prefillKilograms: kg("60")))
    }

    @Test("a suggestion of zero is no target, so last time fills the set")
    func zeroSuggestionFallsBack() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "0", reps: 8), history: history, units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8", prefillKilograms: kg("80")))
    }

    @Test("without a suggestion the newest finished session's first set fills the set")
    func fromLastSession() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history, units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8", prefillKilograms: kg("80")))
    }

    @Test("history is read newest first whatever order it arrives in")
    func historyOrder() {
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: history.reversed(), units: .metric)
        #expect(draft == SetDraft(weight: "80", reps: "8", prefillKilograms: kg("80")))
    }

    @Test("last time's first set fills the set, not its last")
    func takesFirstSetOfLastSession() {
        let sessions = [
            F.session(id: 30, day: 2, entries: [(F.bench, [F.set(1, reps: 10, weight: "70"), F.set(2, reps: 6, weight: "72.5")])]),
        ]
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .metric)
        #expect(draft == SetDraft(weight: "70", reps: "10", prefillKilograms: kg("70")))
    }

    @Test("a metric weight off the plate grid comes back as it was lifted")
    func metricOffGrid() {
        for (stored, shown) in [("24", "24"), ("22", "22"), ("81.25", "81.25")] {
            let sessions = [F.session(id: 31, day: 2, entries: [(F.bench, [F.set(1, reps: 8, weight: stored)])])]
            let draft = SetPrefill.draft(
                exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .metric)
            #expect(draft == SetDraft(weight: shown, reps: "8", prefillKilograms: kg(stored)))
        }
    }

    @Test("last time in pounds comes back as the pounds typed")
    func fromLastSessionInPounds() {
        // 175 lb is stored as 79.38 kg, which shows as 175.0 lb again.
        let sessions = [F.session(id: 32, day: 2, entries: [(F.bench, [F.set(1, reps: 8, weight: "79.38")])])]
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .imperial)
        #expect(draft == SetDraft(weight: "175", reps: "8", prefillKilograms: kg("79.38")))
        // 12.5 lb is stored as 5.67 kg and is not pushed up to a 5 lb plate.
        let dumbbell = [F.session(id: 33, day: 2, entries: [(F.bench, [F.set(1, reps: 12, weight: "5.67")])])]
        let light = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: dumbbell, units: .imperial)
        #expect(light == SetDraft(weight: "12.5", reps: "12", prefillKilograms: kg("5.67")))
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
        #expect(draft == SetDraft(weight: "70", reps: "10", prefillKilograms: kg("70")))
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
        let sessions = [F.session(id: 34, day: 2, entries: [(F.bench, [F.set(1, reps: 8, weight: "79.38")])])]
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .imperial)
        let logged = try draft.loggedSet(units: .imperial)
        #expect(logged.reps == 8)
        #expect(logged.weight == Decimal(string: "79.38", locale: Locale(identifier: "en_US_POSIX")))
    }

    @Test("an untouched suggestion in pounds logs the suggested kilograms exactly")
    func untouchedSuggestionInPounds() throws {
        // 102.5 kg shows as 226 lb, and 226 lb alone would log as 102.51 kg.
        let draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: [], units: .imperial)
        #expect(try draft.loggedSet(units: .imperial).weight == kg("102.5"))
    }

    @Test("a metric set repeated after switching to pounds logs the same kilograms")
    func untouchedMetricSetInPounds() throws {
        // 100 kg shows as 220.5 lb, and 220.5 lb alone would log as 100.02 kg.
        let today = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [F.set(1, reps: 5, weight: "100")],
            suggestion: nil, history: [], units: .imperial)
        #expect(today.weight == "220.5")
        #expect(try today.loggedSet(units: .imperial).weight == kg("100"))
        let sessions = [F.session(id: 35, day: 2, entries: [(F.bench, [F.set(1, reps: 5, weight: "100")])])]
        let last = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [], suggestion: nil, history: sessions, units: .imperial)
        #expect(try last.loggedSet(units: .imperial).weight == kg("100"))
    }

    @Test("an edited weight logs what was typed, not the prefill")
    func editedPrefillLogsTyped() throws {
        var draft = SetPrefill.draft(
            exerciseId: F.bench.id, loggedToday: [],
            suggestion: F.suggestion(weight: "102.5", reps: 8), history: [], units: .imperial)
        draft.weight = "230"
        #expect(try draft.loggedSet(units: .imperial).weight == kg("104.33"))
    }
}
