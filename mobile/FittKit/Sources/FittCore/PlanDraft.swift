import Foundation

/// The body of `POST /api/users/{id}/workout-plans` and of `PUT .../{planId}`.
/// Mirrors the backend `CreateWorkoutPlanRequest` and `UpdateWorkoutPlanRequest`
/// records, which have the same fields, and the two records nested in them.
public struct NewWorkoutPlan: Encodable, Equatable, Sendable {
    public struct Day: Encodable, Equatable, Sendable {
        public let orderIndex: Int
        public let dayLabel: String
        public let exercises: [Slot]
    }

    public struct Slot: Encodable, Equatable, Sendable {
        public let exerciseId: Int64
        public let sets: Int
        public let reps: Int
    }

    public let name: String
    public let daysPerWeek: Int
    public let splitType: SplitType
    public let days: [Day]
}

/// A plan being built in the form. `plan()` turns it into a request or reports
/// which fields are wrong, so the screen holds no rules of its own.
public struct PlanDraft: Equatable, Sendable {
    public static let maxNameLength = 100
    public static let maxLabelLength = 50
    public static let daysPerWeek = 1...7
    public static let dayCount = 1...14
    public static let maxExercisesPerDay = 50
    public static let sets = 1...20
    public static let reps = 1...200

    public struct Slot: Equatable, Identifiable, Sendable {
        public let id: UUID
        public let exercise: Exercise
        public var sets: Int
        public var reps: Int

        public init(id: UUID = UUID(), exercise: Exercise, sets: Int = 3, reps: Int = 10) {
            self.id = id
            self.exercise = exercise
            self.sets = sets
            self.reps = reps
        }
    }

    public struct Day: Equatable, Identifiable, Sendable {
        public let id: UUID
        public var label: String
        public var slots: [Slot]

        public init(id: UUID = UUID(), label: String, slots: [Slot] = []) {
            self.id = id
            self.label = label
            self.slots = slots
        }
    }

    public var name = ""
    public private(set) var splitType: SplitType
    public var daysPerWeek: Int
    public var days: [Day]

    public init(splitType: SplitType = .pushPullLegs) {
        self.splitType = splitType
        self.daysPerWeek = Self.suggestedDaysPerWeek(for: splitType)
        self.days = Self.suggestedLabels(for: splitType).map { Day(label: $0) }
    }

    /// A draft holding a saved plan, for the edit form. Days and exercises are
    /// put in `orderIndex` order, because the position in the draft is the order
    /// that gets sent back.
    public init(plan: WorkoutPlan) {
        self.name = plan.name
        self.splitType = plan.splitType
        self.daysPerWeek = plan.daysPerWeek
        self.days = plan.orderedDays.map { day in
            Day(label: day.dayLabel, slots: day.orderedExercises.map {
                Slot(exercise: $0.exercise, sets: $0.sets, reps: $0.reps)
            })
        }
    }

    /// The day names a split starts with.
    public static func suggestedLabels(for splitType: SplitType) -> [String] {
        switch splitType {
        case .pushPullLegs: ["Push", "Pull", "Legs"]
        case .upperLower: ["Upper", "Lower"]
        case .fullBody: ["Full Body"]
        case .custom: ["Day 1"]
        }
    }

    public static func suggestedDaysPerWeek(for splitType: SplitType) -> Int {
        switch splitType {
        case .pushPullLegs: 3
        case .upperLower: 4
        case .fullBody: 3
        case .custom: 3
        }
    }

    /// Changes the split. The suggested days and frequency replace the current
    /// ones only while the user has not touched them, so picking a split never
    /// throws away exercises already added.
    public mutating func setSplitType(_ newValue: SplitType) {
        let untouched = days.map(\.label) == Self.suggestedLabels(for: splitType)
            && days.allSatisfy(\.slots.isEmpty)
        if untouched {
            days = Self.suggestedLabels(for: newValue).map { Day(label: $0) }
        }
        if daysPerWeek == Self.suggestedDaysPerWeek(for: splitType) {
            daysPerWeek = Self.suggestedDaysPerWeek(for: newValue)
        }
        splitType = newValue
    }

    public var canAddDay: Bool { days.count < Self.dayCount.upperBound }
    public var canRemoveDay: Bool { days.count > Self.dayCount.lowerBound }

    public mutating func addDay() {
        guard canAddDay else { return }
        days.append(Day(label: "Day \(days.count + 1)"))
    }

    public mutating func removeDay(_ id: UUID) {
        guard canRemoveDay else { return }
        days.removeAll { $0.id == id }
    }

    public mutating func add(_ exercise: Exercise, toDay id: UUID) {
        guard let index = days.firstIndex(where: { $0.id == id }),
              days[index].slots.count < Self.maxExercisesPerDay
        else { return }
        days[index].slots.append(Slot(exercise: exercise))
    }

    /// Whether the day can move `offset` places, where -1 is one place earlier.
    public func canMoveDay(_ id: UUID, by offset: Int) -> Bool {
        guard let index = days.firstIndex(where: { $0.id == id }) else { return false }
        return offset != 0 && days.indices.contains(index + offset)
    }

    /// Moves a day `offset` places, keeping the other days in their order.
    public mutating func moveDay(_ id: UUID, by offset: Int) {
        guard canMoveDay(id, by: offset), let index = days.firstIndex(where: { $0.id == id }) else { return }
        let day = days.remove(at: index)
        days.insert(day, at: index + offset)
    }

    /// Moves exercises inside one day. `source` and `destination` mean what they
    /// mean in a list's move action: `destination` is a position in the list as
    /// it is before anything is taken out.
    public mutating func moveSlots(inDay id: UUID, from source: IndexSet, to destination: Int) {
        guard let index = days.firstIndex(where: { $0.id == id }) else { return }
        var slots = days[index].slots
        guard source.allSatisfy(slots.indices.contains), (0...slots.count).contains(destination) else { return }

        let moving = source.map { slots[$0] }
        let insertAt = destination - source.count(in: 0..<destination)
        for offset in source.reversed() {
            slots.remove(at: offset)
        }
        slots.insert(contentsOf: moving, at: insertAt)
        days[index].slots = slots
    }

    /// The request for this draft. Field keys follow the server's names for
    /// nested fields, such as `days[1].dayLabel`.
    public func plan() throws(ValidationFailure) -> NewWorkoutPlan {
        var problems: [String: String] = [:]

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            problems["name"] = "Plan name is required"
        } else if trimmedName.count > Self.maxNameLength {
            problems["name"] = "Plan name must be \(Self.maxNameLength) characters or fewer"
        }
        if !Self.daysPerWeek.contains(daysPerWeek) {
            problems["daysPerWeek"] = "Choose between 1 and 7 days a week"
        }
        if !Self.dayCount.contains(days.count) {
            problems["days"] = "A plan needs between 1 and 14 training days"
        }

        var requestDays: [NewWorkoutPlan.Day] = []
        for (index, day) in days.enumerated() {
            let label = day.label.trimmingCharacters(in: .whitespacesAndNewlines)
            if label.isEmpty {
                problems["days[\(index)].dayLabel"] = "Day label is required"
            } else if label.count > Self.maxLabelLength {
                problems["days[\(index)].dayLabel"] = "Day label must be \(Self.maxLabelLength) characters or fewer"
            }
            if day.slots.count > Self.maxExercisesPerDay {
                problems["days[\(index)].exercises"] = "A day can have at most \(Self.maxExercisesPerDay) exercises"
            }
            if day.slots.contains(where: { !Self.sets.contains($0.sets) || !Self.reps.contains($0.reps) }) {
                problems["days[\(index)].exercises"] = "Sets must be 1 to 20 and reps 1 to 200"
            }
            // The position in the list is the order, so two days can never share an index.
            requestDays.append(NewWorkoutPlan.Day(
                orderIndex: index,
                dayLabel: label,
                exercises: day.slots.map { NewWorkoutPlan.Slot(exerciseId: $0.exercise.id, sets: $0.sets, reps: $0.reps) }
            ))
        }

        guard problems.isEmpty else { throw ValidationFailure(fields: problems) }
        return NewWorkoutPlan(name: trimmedName, daysPerWeek: daysPerWeek, splitType: splitType, days: requestDays)
    }
}

/// The exercise library arranged for a picker.
public enum ExerciseCatalog {
    public struct Group: Equatable, Identifiable, Sendable {
        public let muscleGroup: MuscleGroup
        public let exercises: [Exercise]
        public var id: MuscleGroup { muscleGroup }
    }

    /// Exercises whose name contains `query`, ignoring case and accents, grouped by
    /// muscle group in the server's enum order and sorted by name. Empty groups are dropped.
    public static func groups(_ exercises: [Exercise], matching query: String = "") -> [Group] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        let matches = needle.isEmpty ? exercises : exercises.filter {
            $0.name.range(of: needle, options: [.caseInsensitive, .diacriticInsensitive]) != nil
        }
        return MuscleGroup.allCases.compactMap { muscleGroup in
            let members = matches
                .filter { $0.muscleGroup == muscleGroup }
                .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            return members.isEmpty ? nil : Group(muscleGroup: muscleGroup, exercises: members)
        }
    }
}
