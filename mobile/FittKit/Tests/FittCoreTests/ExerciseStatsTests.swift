import Foundation
import Testing
@testable import FittCore

@Suite("Exercise stats")
struct ExerciseStatsTests {
    private let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    private let pullUp = Exercise(id: 2, name: "Pull Up", muscleGroup: .back, custom: false)
    private let squat = Exercise(id: 3, name: "Back Squat", muscleGroup: .legs, custom: false)

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        return calendar
    }

    private func kg(_ text: String) -> Decimal {
        Decimal(string: text) ?? 0
    }

    private func date(_ text: String) -> Date {
        ServerInstant.parse(text) ?? .distantPast
    }

    /// One finished session with one entry per exercise. Each set is (weight, reps), nil weight for bodyweight.
    private func session(_ id: Int64, _ startedAt: String, _ entries: [(Exercise, [(String?, Int)])], finished: Bool = true) -> WorkoutSession {
        let start = date(startedAt)
        return WorkoutSession(
            id: id,
            planId: nil,
            startedAt: start,
            finishedAt: finished ? start.addingTimeInterval(3600) : nil,
            exerciseEntries: entries.enumerated().map { index, entry in
                ExerciseEntry(
                    id: id * 100 + Int64(index),
                    orderIndex: index,
                    exercise: entry.0,
                    sets: entry.1.enumerated().map { number, set in
                        SetEntry(id: id * 1000 + Int64(number), setNumber: number + 1, reps: set.1,
                                 weight: set.0.map(kg), rpe: nil, completed: true)
                    }
                )
            }
        )
    }

    // MARK: Estimated one-rep max

    @Test("Epley: weight times one plus reps over thirty, to two places", arguments: [
        ("100", 10, "133.33"),
        ("100", 5, "116.67"),
        ("80", 12, "112"),
        ("60", 2, "64"),
    ])
    func epley(weight: String, reps: Int, expected: String) {
        #expect(OneRepMax.estimate(weight: kg(weight), reps: reps) == kg(expected))
    }

    @Test("one rep counts as the weight itself")
    func oneRep() {
        #expect(OneRepMax.estimate(weight: kg("142.5"), reps: 1) == kg("142.5"))
    }

    @Test("sets above twelve reps, with no reps, or without weight give no estimate", arguments: [
        ("100" as String?, 13 as Int?),
        ("100", 0),
        ("100", nil),
        (nil, 8),
        ("0", 8),
    ])
    func noEstimate(weight: String?, reps: Int?) {
        #expect(OneRepMax.estimate(weight: weight.map(kg), reps: reps) == nil)
    }

    // MARK: Best set and best estimate

    @Test("the best set is the heaviest, and the best estimate can come from a lighter set")
    func bestSetAndEstimate() {
        let sessions = [
            session(1, "2026-11-02T08:00:00Z", [(bench, [("100", 3), ("90", 10)])]),
            session(2, "2026-11-05T08:00:00Z", [(bench, [("95", 5)])]),
        ]

        let stats = ExerciseStats(exercise: bench, sessions: sessions, calendar: utc)

        #expect(stats.bestSet?.set.weight == kg("100"))
        #expect(stats.bestSet?.set.reps == 3)
        #expect(stats.bestEstimate?.value == kg("120"))
        #expect(stats.bestEstimate?.from.set.reps == 10)
        #expect(stats.bestEstimate?.from.sessionId == 1)
        #expect(stats.sessionCount == 2)
    }

    @Test("a tie on weight goes to more reps, then to the earlier date")
    func bestSetTies() {
        let sessions = [
            session(3, "2026-11-09T08:00:00Z", [(bench, [("100", 5)])]),
            session(1, "2026-11-02T08:00:00Z", [(bench, [("100", 5), ("100", 4)])]),
            session(2, "2026-11-05T08:00:00Z", [(bench, [("100", 3)])]),
        ]

        let stats = ExerciseStats(exercise: bench, sessions: sessions, calendar: utc)

        #expect(stats.bestSet?.sessionId == 1)
        #expect(stats.bestSet?.set.setNumber == 1)
        #expect(stats.bestEstimate?.from.sessionId == 1)
    }

    @Test("a tie on the estimate goes to the earlier date")
    func estimateTie() {
        let sessions = [
            session(2, "2026-11-05T08:00:00Z", [(bench, [("120", 1)])]),
            session(1, "2026-11-02T08:00:00Z", [(bench, [("90", 10)])]),
        ]

        #expect(ExerciseStats(exercise: bench, sessions: sessions, calendar: utc).bestEstimate?.from.sessionId == 1)
    }

    @Test("bodyweight sets count toward sessions but not toward weights")
    func bodyweightSets() {
        let sessions = [
            session(1, "2026-11-02T08:00:00Z", [(pullUp, [(nil, 10), (nil, 8)])]),
            session(2, "2026-11-05T08:00:00Z", [(pullUp, [(nil, 9), ("10", 5)])]),
        ]

        let stats = ExerciseStats(exercise: pullUp, sessions: sessions, calendar: utc)

        #expect(stats.sessionCount == 2)
        #expect(stats.bestSet?.set.weight == kg("10"))
        #expect(stats.bestEstimate?.value == kg("11.67"))
        #expect(stats.series.map(\.day) == [CalendarDate(year: 2026, month: 11, day: 5)])
    }

    @Test("a set above twelve reps is a best set but gives no estimate")
    func highRepSet() {
        let stats = ExerciseStats(exercise: bench, sessions: [session(1, "2026-11-02T08:00:00Z", [(bench, [("60", 15)])])], calendar: utc)

        #expect(stats.bestSet?.set.reps == 15)
        #expect(stats.bestEstimate == nil)
        #expect(stats.series == [ExerciseStats.Point(day: CalendarDate(year: 2026, month: 11, day: 2), estimatedOneRepMax: nil, topWeight: kg("60"))])
    }

    @Test("unfinished sessions and other exercises are left out")
    func leavesOutOthers() {
        let sessions = [
            session(1, "2026-11-02T08:00:00Z", [(squat, [("140", 5)])]),
            session(2, "2026-11-05T08:00:00Z", [(bench, [("100", 5)])], finished: false),
        ]

        let stats = ExerciseStats(exercise: bench, sessions: sessions, calendar: utc)

        #expect(stats.sessionCount == 0)
        #expect(stats.bestSet == nil)
        #expect(stats.series.isEmpty)
    }

    // MARK: Series

    @Test("the series has one point a day, oldest first, with the day's best estimate and top weight")
    func seriesByDay() {
        let sessions = [
            session(3, "2026-11-09T08:00:00Z", [(bench, [("105", 3)])]),
            session(1, "2026-11-02T08:00:00Z", [(bench, [("100", 5)])]),
            session(2, "2026-11-02T18:00:00Z", [(bench, [("90", 10), ("102.5", 1)])]),
        ]

        let series = ExerciseStats(exercise: bench, sessions: sessions, calendar: utc).series

        #expect(series == [
            ExerciseStats.Point(day: CalendarDate(year: 2026, month: 11, day: 2), estimatedOneRepMax: kg("120"), topWeight: kg("102.5")),
            ExerciseStats.Point(day: CalendarDate(year: 2026, month: 11, day: 9), estimatedOneRepMax: kg("115.5"), topWeight: kg("105")),
        ])
    }

    @Test("days follow the calendar's time zone, not UTC")
    func seriesInLocalDays() throws {
        var losAngeles = Calendar(identifier: .gregorian)
        losAngeles.timeZone = try #require(TimeZone(identifier: "America/Los_Angeles"))
        let late = session(1, "2026-11-03T03:00:00Z", [(bench, [("100", 5)])])

        #expect(ExerciseStats(exercise: bench, sessions: [late], calendar: losAngeles).series.map(\.day)
            == [CalendarDate(year: 2026, month: 11, day: 2)])
    }

    // MARK: Records

    @Test("records list the exercises trained most that have an estimate")
    func records() {
        let sessions = [
            session(1, "2026-11-02T08:00:00Z", [(bench, [("100", 5)]), (pullUp, [(nil, 10)])]),
            session(2, "2026-11-04T08:00:00Z", [(bench, [("100", 6)]), (squat, [("140", 5)]), (pullUp, [(nil, 10)])]),
            session(3, "2026-11-06T08:00:00Z", [(pullUp, [(nil, 10)])]),
        ]

        let records = ExerciseStats.records(sessions, limit: 5, calendar: utc)

        #expect(records.map(\.exercise.name) == ["Barbell Bench Press", "Back Squat"])
        #expect(ExerciseStats.records(sessions, limit: 1, calendar: utc).map(\.exercise.id) == [1])
    }

    @Test("an empty history has no records, no series and no volume")
    func emptyHistory() {
        #expect(ExerciseStats.records([], limit: 5, calendar: utc).isEmpty)
        let stats = ExerciseStats(exercise: bench, sessions: [], calendar: utc)
        #expect(stats.sessionCount == 0 && stats.bestSet == nil && stats.bestEstimate == nil && stats.series.isEmpty)
        let weeks = MuscleWeek.weeks([], count: 8, now: date("2026-11-24T08:00:00Z"), calendar: utc)
        #expect(weeks.count == 8)
        #expect(weeks.allSatisfy { $0.volume.isEmpty && $0.sets.isEmpty })
    }

    // MARK: Weekly volume

    @Test("weeks start on Monday, newest first, and sum weight times reps per muscle")
    func weeklyVolume() {
        let sessions = [
            session(1, "2026-11-23T08:00:00Z", [(bench, [("100", 5), ("100", 5)]), (pullUp, [(nil, 10)])]),
            session(2, "2026-11-22T08:00:00Z", [(squat, [("140", 5)]), (bench, [("50", 10)])]),
            session(3, "2026-09-01T08:00:00Z", [(squat, [("140", 5)])]),
        ]

        let weeks = MuscleWeek.weeks(sessions, count: 2, now: date("2026-11-24T08:00:00Z"), calendar: utc)

        #expect(weeks.map(\.start) == [CalendarDate(year: 2026, month: 11, day: 23), CalendarDate(year: 2026, month: 11, day: 16)])
        #expect(weeks[0].volume == [.chest: 1000, .back: 0])
        #expect(weeks[0].sets == [.chest: 2, .back: 1])
        #expect(weeks[1].volume == [.legs: 700, .chest: 500])
        #expect(weeks[1].sets == [.legs: 1, .chest: 1])
    }

    @Test("a week that spans the new year stays one week")
    func weekAcrossYearEnd() {
        let sessions = [
            session(1, "2026-12-31T08:00:00Z", [(bench, [("100", 1)])]),
            session(2, "2027-01-03T08:00:00Z", [(bench, [("100", 2)])]),
            session(3, "2027-01-04T08:00:00Z", [(bench, [("100", 3)])]),
        ]

        let weeks = MuscleWeek.weeks(sessions, count: 2, now: date("2027-01-05T08:00:00Z"), calendar: utc)

        #expect(weeks.map(\.start) == [CalendarDate(year: 2027, month: 1, day: 4), CalendarDate(year: 2026, month: 12, day: 28)])
        #expect(weeks[0].volume[.chest] == 300)
        #expect(weeks[1].volume[.chest] == 300)
        #expect(weeks[1].sets[.chest] == 2)
    }

    @Test("a Monday in UTC that is still Sunday locally belongs to the week before")
    func weekInLocalTime() throws {
        var losAngeles = Calendar(identifier: .gregorian)
        losAngeles.timeZone = try #require(TimeZone(identifier: "America/Los_Angeles"))
        let sundayEvening = session(1, "2026-11-23T02:00:00Z", [(bench, [("100", 5)])])

        let weeks = MuscleWeek.weeks([sundayEvening], count: 2, now: date("2026-11-24T20:00:00Z"), calendar: losAngeles)

        #expect(weeks.map(\.start) == [CalendarDate(year: 2026, month: 11, day: 23), CalendarDate(year: 2026, month: 11, day: 16)])
        #expect(weeks[0].sets.isEmpty)
        #expect(weeks[1].sets[.chest] == 1)
    }

    @Test("a calendar that starts weeks on Sunday still gets Monday weeks")
    func mondayWhateverTheLocale() {
        var sundayFirst = utc
        sundayFirst.firstWeekday = 1
        let sunday = session(1, "2026-11-22T08:00:00Z", [(bench, [("100", 5)])])

        let weeks = MuscleWeek.weeks([sunday], count: 1, now: date("2026-11-22T20:00:00Z"), calendar: sundayFirst)

        #expect(weeks.map(\.start) == [CalendarDate(year: 2026, month: 11, day: 16)])
        #expect(weeks[0].sets[.chest] == 1)
    }
}
