import Foundation

/// What the user did for an exercise the last time they trained it: the newest
/// finished workout that has sets for it.
public struct LastTime: Equatable, Sendable {
    /// When that workout started.
    public let date: Date
    /// Its sets for the exercise, in order.
    public let sets: [SetEntry]

    public init(date: Date, sets: [SetEntry]) {
        self.date = date
        self.sets = sets
    }

    /// Nil when no finished workout has a set for the exercise. A workout in
    /// progress is never last time.
    public static func find(exerciseId: Int64, in sessions: [WorkoutSession]) -> LastTime? {
        for session in SessionHistory.finished(sessions) {
            let sets = session.orderedEntries
                .filter { $0.exercise.id == exerciseId }
                .flatMap(\.orderedSets)
            if !sets.isEmpty {
                return LastTime(date: session.startedAt, sets: sets)
            }
        }
        return nil
    }

    /// "80 kg × 8, 8, 7". A change of weight starts a new group:
    /// "80 kg × 8, 8; 85 kg × 6". Sets with no weight read "12, 10 reps".
    public func text(units: UnitsPreference) -> String {
        groups.map { group in
            let reps = group.reps.map(String.init).joined(separator: ", ")
            guard let weight = group.weight else { return "\(reps) reps" }
            return "\(units.shown(kilograms: weight).formatted()) \(units.weightLabel) × \(reps)"
        }
        .joined(separator: "; ")
    }

    /// The same for VoiceOver, in words: "Last time, 80 kilograms for 8, 8 and 7 reps".
    public func accessibilityText(units: UnitsPreference) -> String {
        let spoken = groups.map { group in
            let reps = Self.list(group.reps.map(String.init))
            let repsWord = group.reps == [1] ? "rep" : "reps"
            guard let weight = group.weight else { return "\(reps) \(repsWord)" }
            let shown = units.shown(kilograms: weight)
            return "\(shown.formatted()) \(units.weightName(plural: shown != 1)) for \(reps) \(repsWord)"
        }
        return "Last time, " + spoken.joined(separator: ", then ")
    }

    /// Runs of sets at the same weight, in order.
    private var groups: [(weight: Decimal?, reps: [Int])] {
        var result: [(weight: Decimal?, reps: [Int])] = []
        for set in sets {
            let reps = set.reps ?? 0
            if let last = result.indices.last, result[last].weight == set.weight {
                result[last].reps.append(reps)
            } else {
                result.append((set.weight, [reps]))
            }
        }
        return result
    }

    /// "8", "8 and 8", "8, 8 and 7".
    private static func list(_ items: [String]) -> String {
        guard let last = items.last, items.count > 1 else { return items.first ?? "" }
        return items.dropLast().joined(separator: ", ") + " and " + last
    }
}

extension UnitsPreference {
    /// The unit in words for VoiceOver: "kilograms", "pound".
    public func weightName(plural: Bool = true) -> String {
        switch self {
        case .metric: plural ? "kilograms" : "kilogram"
        case .imperial: plural ? "pounds" : "pound"
        }
    }
}
