import Foundation

/// The estimated one-rep max: the most a lifter could likely lift once, worked
/// out from a set of several reps. An estimate from logged sets, not a test.
public enum OneRepMax {
    /// Past twelve reps the formula drifts too far to be useful.
    public static let reps = 1...12

    /// Epley: weight × (1 + reps / 30), in kilograms to two places. One rep is
    /// the weight itself. Nil for a set without weight or outside `reps`.
    public static func estimate(weight: Decimal?, reps: Int?) -> Decimal? {
        guard let weight, weight > 0, let reps, Self.reps.contains(reps) else { return nil }
        guard reps > 1 else { return weight }
        return (weight * Decimal(30 + reps) / 30).rounded(scale: 2)
    }
}

/// Records and progress for one exercise, worked out on the device from the
/// finished sessions. Weights are in kilograms.
public struct ExerciseStats: Equatable, Sendable {
    /// A set with the session it was logged in.
    public struct DatedSet: Equatable, Sendable {
        public let set: SetEntry
        public let date: Date
        public let sessionId: Int64

        /// Earlier sessions first, then the set's place in its session.
        fileprivate func isBefore(_ other: DatedSet) -> Bool {
            (date, sessionId, set.setNumber) < (other.date, other.sessionId, other.set.setNumber)
        }
    }

    public struct Estimate: Equatable, Sendable {
        public let value: Decimal
        public let from: DatedSet
    }

    /// One training day. Either value is nil when no set that day qualified.
    public struct Point: Equatable, Sendable, Identifiable {
        public let day: CalendarDate
        public let estimatedOneRepMax: Decimal?
        public let topWeight: Decimal?
        public var id: CalendarDate { day }

        public init(day: CalendarDate, estimatedOneRepMax: Decimal?, topWeight: Decimal?) {
            self.day = day
            self.estimatedOneRepMax = estimatedOneRepMax
            self.topWeight = topWeight
        }
    }

    public let exercise: Exercise
    /// The heaviest set. A tie goes to more reps, then to the earlier set.
    public let bestSet: DatedSet?
    /// The highest estimate. A tie goes to the earlier set.
    public let bestEstimate: Estimate?
    /// Days with a weighted set, oldest first.
    public let series: [Point]
    /// Finished sessions with at least one set of this exercise.
    public let sessionCount: Int

    public init(exercise: Exercise, sessions: [WorkoutSession], calendar: Calendar = CalendarDate.gregorian) {
        self.exercise = exercise
        var sets: [DatedSet] = []
        var sessionIds = Set<Int64>()
        for session in sessions where session.isFinished {
            for entry in session.exerciseEntries where entry.exercise.id == exercise.id {
                for set in entry.sets {
                    sets.append(DatedSet(set: set, date: session.startedAt, sessionId: session.id))
                    sessionIds.insert(session.id)
                }
            }
        }
        sets.sort { $0.isBefore($1) }
        sessionCount = sessionIds.count

        var bestSet: DatedSet?
        var bestEstimate: Estimate?
        var days: [CalendarDate: Point] = [:]
        for dated in sets {
            guard let weight = dated.set.weight, weight > 0 else { continue }
            let reps = dated.set.reps ?? 0
            // The sets run oldest first, so only a strictly better set replaces the best.
            if bestSet.map({ ($0.set.weight ?? 0, $0.set.reps ?? 0) < (weight, reps) }) ?? true {
                bestSet = dated
            }
            let estimate = OneRepMax.estimate(weight: weight, reps: reps)
            if let estimate, estimate > bestEstimate?.value ?? 0 {
                bestEstimate = Estimate(value: estimate, from: dated)
            }
            let day = CalendarDate(dated.date, calendar: calendar)
            let point = days[day]
            days[day] = Point(
                day: day,
                estimatedOneRepMax: [point?.estimatedOneRepMax, estimate].compactMap { $0 }.max(),
                topWeight: max(point?.topWeight ?? 0, weight)
            )
        }
        self.bestSet = bestSet
        self.bestEstimate = bestEstimate
        series = days.values.sorted { $0.day < $1.day }
    }

    /// The exercises trained in the most sessions that have an estimated
    /// one-rep max, most trained first, then by name.
    public static func records(_ sessions: [WorkoutSession], limit: Int, calendar: Calendar = CalendarDate.gregorian) -> [ExerciseStats] {
        var exercises: [Int64: Exercise] = [:]
        for session in sessions where session.isFinished {
            for entry in session.exerciseEntries where !entry.sets.isEmpty {
                exercises[entry.exercise.id] = entry.exercise
            }
        }
        let ranked = exercises.values
            .map { ExerciseStats(exercise: $0, sessions: sessions, calendar: calendar) }
            .filter { $0.bestEstimate != nil }
            .sorted { ($1.sessionCount, $0.exercise.name, $0.exercise.id) < ($0.sessionCount, $1.exercise.name, $1.exercise.id) }
        return Array(ranked.prefix(max(limit, 0)))
    }
}

/// The work done for each muscle group in one week, Monday to Sunday.
public struct MuscleWeek: Equatable, Sendable, Identifiable {
    /// The Monday the week starts on.
    public let start: CalendarDate
    /// Weight × reps in kilograms. A muscle trained only with bodyweight sets is here with zero.
    public let volume: [MuscleGroup: Decimal]
    /// Sets logged, bodyweight sets included.
    public let sets: [MuscleGroup: Int]
    public var id: CalendarDate { start }

    /// The last `count` weeks up to the one holding `now`, newest first, empty
    /// weeks included. Weeks start on Monday in the calendar's time zone,
    /// whatever day the calendar's own weeks start on.
    public static func weeks(_ sessions: [WorkoutSession], count: Int, now: Date, calendar: Calendar = CalendarDate.gregorian) -> [MuscleWeek] {
        var calendar = calendar
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 4
        func weekStart(_ date: Date) -> CalendarDate? {
            calendar.dateInterval(of: .weekOfYear, for: date).map { CalendarDate($0.start, calendar: calendar) }
        }
        guard let current = calendar.dateInterval(of: .weekOfYear, for: now)?.start else { return [] }
        let starts = (0..<max(count, 0)).compactMap { offset in
            calendar.date(byAdding: .weekOfYear, value: -offset, to: current).map { CalendarDate($0, calendar: calendar) }
        }

        var volume: [CalendarDate: [MuscleGroup: Decimal]] = [:]
        var sets: [CalendarDate: [MuscleGroup: Int]] = [:]
        for session in sessions where session.isFinished {
            guard let start = weekStart(session.startedAt), starts.contains(start) else { continue }
            for entry in session.exerciseEntries {
                let muscle = entry.exercise.muscleGroup
                for set in entry.sets {
                    volume[start, default: [:]][muscle, default: 0] += (set.weight ?? 0) * Decimal(set.reps ?? 0)
                    sets[start, default: [:]][muscle, default: 0] += 1
                }
            }
        }
        return starts.map { MuscleWeek(start: $0, volume: volume[$0] ?? [:], sets: sets[$0] ?? [:]) }
    }
}
