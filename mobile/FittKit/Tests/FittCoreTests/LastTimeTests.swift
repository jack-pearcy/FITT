import FittCore
import Foundation
import Testing

@Suite("LastTime")
struct LastTimeTests {
    private typealias F = PrefillFixtures

    @Test("the newest finished session with the exercise is last time")
    func findsNewest() throws {
        let sessions = [
            F.session(id: 10, day: 1, entries: [(F.bench, [F.set(1, reps: 8, weight: "75")])]),
            F.session(id: 12, day: 5, finished: false, entries: [(F.bench, [F.set(1, reps: 1, weight: "200")])]),
            F.session(id: 11, day: 3, entries: [(F.bench, [F.set(2, reps: 8, weight: "80"), F.set(1, reps: 8, weight: "80"), F.set(3, reps: 7, weight: "80")])]),
            F.session(id: 13, day: 4, entries: [(F.squat, [F.set(1, reps: 5, weight: "120")])]),
        ]
        let last = try #require(LastTime.find(exerciseId: F.bench.id, in: sessions))
        #expect(last.date == sessions[2].startedAt)
        #expect(last.sets.map(\.setNumber) == [1, 2, 3])
        #expect(last.text(units: .metric) == "80 kg × 8, 8, 7")
        #expect(last.accessibilityText(units: .metric) == "Last time, 80 kilograms for 8, 8 and 7 reps")
    }

    @Test("no finished session with sets for the exercise means no last time")
    func none() {
        let sessions = [F.session(id: 10, day: 1, entries: [(F.bench, [])])]
        #expect(LastTime.find(exerciseId: F.bench.id, in: sessions) == nil)
        #expect(LastTime.find(exerciseId: F.bench.id, in: []) == nil)
    }

    @Test("pounds are shown in pounds")
    func pounds() throws {
        let sessions = [F.session(id: 10, day: 1, entries: [(F.bench, [F.set(1, reps: 5, weight: "100"), F.set(2, reps: 5, weight: "100")])])]
        let last = try #require(LastTime.find(exerciseId: F.bench.id, in: sessions))
        #expect(last.text(units: .imperial) == "220.5 lb × 5, 5")
        #expect(last.accessibilityText(units: .imperial) == "Last time, 220.5 pounds for 5 and 5 reps")
    }

    @Test("a change of weight starts a new group")
    func groups() throws {
        let sessions = [F.session(id: 10, day: 1, entries: [(F.bench, [
            F.set(1, reps: 8, weight: "80"), F.set(2, reps: 8, weight: "80"), F.set(3, reps: 6, weight: "85"),
        ])])]
        let last = try #require(LastTime.find(exerciseId: F.bench.id, in: sessions))
        #expect(last.text(units: .metric) == "80 kg × 8, 8; 85 kg × 6")
        #expect(last.accessibilityText(units: .metric)
            == "Last time, 80 kilograms for 8 and 8 reps, then 85 kilograms for 6 reps")
    }

    @Test("bodyweight sets read as reps alone")
    func bodyweight() throws {
        let sessions = [F.session(id: 10, day: 1, entries: [(F.bench, [F.set(1, reps: 12, weight: nil), F.set(2, reps: 10, weight: nil)])])]
        let last = try #require(LastTime.find(exerciseId: F.bench.id, in: sessions))
        #expect(last.text(units: .metric) == "12, 10 reps")
        #expect(last.accessibilityText(units: .metric) == "Last time, 12 and 10 reps")
    }

    @Test("one set of one rep reads in the singular")
    func singular() throws {
        let sessions = [F.session(id: 10, day: 1, entries: [(F.bench, [F.set(1, reps: 1, weight: "1")])])]
        let last = try #require(LastTime.find(exerciseId: F.bench.id, in: sessions))
        #expect(last.text(units: .metric) == "1 kg × 1")
        #expect(last.accessibilityText(units: .metric) == "Last time, 1 kilogram for 1 rep")
    }
}
