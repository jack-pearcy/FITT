import Foundation

public enum MuscleGroup: String, Codable, CaseIterable, Sendable {
    case chest = "CHEST"
    case back = "BACK"
    case shoulders = "SHOULDERS"
    case arms = "ARMS"
    case legs = "LEGS"
    case glutes = "GLUTES"
    case core = "CORE"
}

public enum SplitType: String, Codable, CaseIterable, Sendable {
    case pushPullLegs = "PPL"
    case upperLower = "UPPER_LOWER"
    case fullBody = "FULL_BODY"
    case custom = "CUSTOM"
}

/// Mirrors the backend `ExerciseResponse` record.
public struct Exercise: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let name: String
    public let muscleGroup: MuscleGroup
    /// True for an exercise this user created; false for one from the shared library.
    public let custom: Bool

    public init(id: Int64, name: String, muscleGroup: MuscleGroup, custom: Bool) {
        self.id = id
        self.name = name
        self.muscleGroup = muscleGroup
        self.custom = custom
    }
}

/// Mirrors the backend `PlanDayExerciseResponse` record: one exercise as prescribed on a plan day.
public struct PlanDayExercise: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let orderIndex: Int
    public let sets: Int
    public let reps: Int
    public let exercise: Exercise

    public init(id: Int64, orderIndex: Int, sets: Int, reps: Int, exercise: Exercise) {
        self.id = id
        self.orderIndex = orderIndex
        self.sets = sets
        self.reps = reps
        self.exercise = exercise
    }
}

/// Mirrors the backend `WorkoutPlanDayResponse` record.
public struct WorkoutPlanDay: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let orderIndex: Int
    public let dayLabel: String
    public let exercises: [PlanDayExercise]

    public init(id: Int64, orderIndex: Int, dayLabel: String, exercises: [PlanDayExercise]) {
        self.id = id
        self.orderIndex = orderIndex
        self.dayLabel = dayLabel
        self.exercises = exercises
    }

    /// The server does not promise an order, so the app sorts by `orderIndex`,
    /// the same key the server's rotation uses.
    public var orderedExercises: [PlanDayExercise] {
        exercises.sorted { $0.orderIndex < $1.orderIndex }
    }
}

/// Mirrors the backend `WorkoutPlanResponse` record.
public struct WorkoutPlan: Decodable, Equatable, Identifiable, Sendable {
    public let id: Int64
    public let name: String
    public let daysPerWeek: Int
    public let splitType: SplitType
    public let active: Bool
    public let days: [WorkoutPlanDay]

    public init(id: Int64, name: String, daysPerWeek: Int, splitType: SplitType, active: Bool, days: [WorkoutPlanDay]) {
        self.id = id
        self.name = name
        self.daysPerWeek = daysPerWeek
        self.splitType = splitType
        self.active = active
        self.days = days
    }

    public var orderedDays: [WorkoutPlanDay] {
        days.sorted { $0.orderIndex < $1.orderIndex }
    }
}

/// Mirrors the backend `NextLiftResponse` record: what to train next.
///
/// The server works out the day from the count of finished sessions, so the
/// app shows this and computes nothing about the rotation itself.
public struct NextLift: Decodable, Equatable, Sendable {
    public let hasActivePlan: Bool
    public let planId: Int64?
    public let planName: String?
    public let daysInRotation: Int
    /// One-based position of `day` in the rotation. Zero when there is no day.
    public let dayNumber: Int
    public let weekInCycle: Int
    public let deload: Bool
    public let alreadyTrainedToday: Bool
    /// Nil when there is no active plan, or the active plan has no days.
    public let day: WorkoutPlanDay?

    public init(
        hasActivePlan: Bool,
        planId: Int64? = nil,
        planName: String? = nil,
        daysInRotation: Int = 0,
        dayNumber: Int = 0,
        weekInCycle: Int = 1,
        deload: Bool = false,
        alreadyTrainedToday: Bool = false,
        day: WorkoutPlanDay? = nil
    ) {
        self.hasActivePlan = hasActivePlan
        self.planId = planId
        self.planName = planName
        self.daysInRotation = daysInRotation
        self.dayNumber = dayNumber
        self.weekInCycle = weekInCycle
        self.deload = deload
        self.alreadyTrainedToday = alreadyTrainedToday
        self.day = day
    }
}
