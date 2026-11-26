import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("Weekly volume screen")
struct MuscleVolumeTests {
    private let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    private let pullUp = Exercise(id: 2, name: "Pull Up", muscleGroup: .back, custom: false)
    private let squat = Exercise(id: 3, name: "Back Squat", muscleGroup: .legs, custom: false)

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        return calendar
    }

    private let english = Locale(identifier: "en_US")

    /// Thursday 26 November 2026, 08:00 UTC. Its week starts on Monday the 23rd.
    private let now = Date(timeIntervalSince1970: 1_795_680_000)

    /// A finished session `daysAgo` days before `now`. Each exercise gets `sets` sets of 100 kg × 5.
    private func session(_ id: Int64, daysAgo: Int, _ entries: [(Exercise, Int)], finished: Bool = true) -> WorkoutSession {
        let start = now.addingTimeInterval(-Double(daysAgo) * 86_400)
        return WorkoutSession(
            id: id,
            planId: nil,
            startedAt: start,
            finishedAt: finished ? start.addingTimeInterval(3600) : nil,
            exerciseEntries: entries.enumerated().map { index, entry in
                ExerciseEntry(id: id * 100 + Int64(index), orderIndex: index, exercise: entry.0, sets: (0..<entry.1).map { number in
                    SetEntry(id: id * 1000 + Int64(index * 10 + number), setNumber: number + 1, reps: 5, weight: 100, rpe: nil, completed: true)
                })
            }
        )
    }

    private func state(_ history: [WorkoutSession]?, error: String? = nil, offline: Bool = false) -> ScreenState<[MuscleWeek]> {
        MuscleVolumeContent.screenState(history: history, errorMessage: error, isOffline: offline, now: now, calendar: utc)
    }

    private func weeks(_ history: [WorkoutSession]) -> [MuscleWeek] {
        guard case .content(let weeks) = state(history) else {
            Issue.record("expected content")
            return []
        }
        return weeks
    }

    @Test("the test clock is the Thursday the cases assume")
    func clock() {
        #expect(CalendarDate(now, calendar: utc) == CalendarDate(year: 2026, month: 11, day: 26))
    }

    @Test("sets per muscle are counted week by week, this week first")
    func twoWeeks() {
        let weeks = weeks([
            session(1, daysAgo: 1, [(bench, 3), (pullUp, 2)]),
            session(2, daysAgo: 8, [(squat, 4), (bench, 1)]),
        ])

        #expect(weeks.count == MuscleVolumeView.weeks)
        #expect(weeks[0].start == CalendarDate(year: 2026, month: 11, day: 23))
        #expect(weeks[0].sets == [.chest: 3, .back: 2])
        #expect(weeks[1].start == CalendarDate(year: 2026, month: 11, day: 16))
        #expect(weeks[1].sets == [.legs: 4, .chest: 1])
        #expect(weeks.dropFirst(2).allSatisfy { $0.sets.isEmpty })
    }

    @Test("an exercise trained in two sessions of one week counts all its sets in that week")
    func twoSessionsInAWeek() {
        let weeks = weeks([
            session(1, daysAgo: 0, [(bench, 3)]),
            session(2, daysAgo: 2, [(bench, 4)]),
            session(3, daysAgo: 7, [(bench, 2)]),
        ])

        #expect(weeks[0].sets == [.chest: 7])
        #expect(weeks[0].volume == [.chest: 3500])
        #expect(weeks[1].sets == [.chest: 2])
    }

    @Test("an empty history is the empty state")
    func emptyHistory() {
        #expect(state([]) == .empty)
    }

    @Test("a history with no finished set in the weeks shown is the empty state")
    func nothingInRange() {
        #expect(state([session(1, daysAgo: 7 * MuscleVolumeView.weeks + 7, [(bench, 3)])]) == .empty)
        #expect(state([session(2, daysAgo: 1, [(bench, 3)], finished: false)]) == .empty)
        #expect(state([session(3, daysAgo: 1, [(bench, 0)])]) == .empty)
    }

    @Test("loading, a failed load and offline pass through")
    func otherStates() {
        #expect(state(nil) == .loading)
        #expect(state(nil, error: "The server gave an unexpected answer. Try again.") == .failed("The server gave an unexpected answer. Try again."))
        #expect(state(nil, error: "Cannot reach the server. Check the connection.", offline: true) == .offline)
    }

    @Test("a week is named for where it sits, then for its Monday")
    func weekTitles() {
        let weeks = weeks([session(1, daysAgo: 1, [(bench, 3)])])

        #expect(MuscleVolumeContent.title(of: weeks[0], in: weeks, calendar: utc) == "This week")
        #expect(MuscleVolumeContent.title(of: weeks[1], in: weeks, calendar: utc) == "Last week")
        #expect(MuscleVolumeContent.title(of: weeks[2], in: weeks, calendar: utc, locale: english) == "Week of Nov 9")
        #expect(MuscleVolumeContent.title(of: weeks[7], in: weeks, calendar: utc, locale: english) == "Week of Oct 5")
    }

    @Test("an older week is named for its Monday in the calendar's time zone, not the device's")
    func weekTitleTimeZone() {
        let weeks = weeks([session(1, daysAgo: 1, [(bench, 3)])])
        #expect(weeks[2].start == CalendarDate(year: 2026, month: 11, day: 9))

        // Midnight on Monday the 9th in each of these zones. They sit 25 hours
        // apart, so on any device at least one is another day on the device's clock.
        for zone in ["Pacific/Kiritimati", "Pacific/Pago_Pago"] {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: zone) ?? .gmt
            #expect(calendar.timeZone.identifier == zone)
            #expect(MuscleVolumeContent.title(of: weeks[2], in: weeks, calendar: calendar, locale: english) == "Week of Nov 9")
        }
    }

    @Test("a row shows volume in the user's unit, to a whole number")
    func volumeText() {
        #expect(MuscleVolumeContent.volumeText(kilograms: 3500, units: .metric, locale: english) == "3,500 kg")
        #expect(MuscleVolumeContent.volumeText(kilograms: 3500, units: .imperial, locale: english) == "7,716 lb")
        #expect(MuscleVolumeContent.volumeText(kilograms: Decimal(string: "1200.4") ?? 0, units: .metric, locale: english) == "1,200 kg")
    }

    @Test("a muscle with only bodyweight sets shows no volume")
    func bodyweightOnlyVolume() {
        #expect(MuscleVolumeContent.volumeText(kilograms: 0, units: .metric, locale: english) == nil)
        #expect(MuscleVolumeContent.volumeText(kilograms: 0, units: .imperial, locale: english) == nil)
    }

    @Test("the spoken summary of a week gives its total and each muscle, most sets first")
    func spokenSummary() {
        let weeks = weeks([
            session(1, daysAgo: 1, [(bench, 1), (pullUp, 3)]),
        ])

        #expect(MuscleVolumeContent.summary(of: weeks[0]) == "4 sets. Back 3 sets, Chest 1 set")
        #expect(MuscleVolumeContent.summary(of: weeks[1]) == "No sets")
    }
}
