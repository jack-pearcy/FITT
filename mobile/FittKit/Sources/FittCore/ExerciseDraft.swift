import Foundation

/// The body of `POST /api/users/{id}/exercises`. Mirrors the backend
/// `CreateExerciseRequest` record.
public struct NewExercise: Encodable, Equatable, Sendable {
    public let name: String
    public let muscleGroup: MuscleGroup
}

/// A custom exercise being typed into the form. `exercise()` turns it into a
/// request or reports which fields are wrong.
public struct ExerciseDraft: Equatable, Sendable {
    public static let maxNameLength = 100

    public var name: String
    public var muscleGroup: MuscleGroup

    public init(name: String = "", muscleGroup: MuscleGroup = .chest) {
        self.name = name
        self.muscleGroup = muscleGroup
    }

    public func exercise() throws(ValidationFailure) -> NewExercise {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            throw ValidationFailure(fields: ["name": "Exercise name is required"])
        }
        if trimmedName.count > Self.maxNameLength {
            throw ValidationFailure(fields: ["name": "Exercise name must be \(Self.maxNameLength) characters or fewer"])
        }
        return NewExercise(name: trimmedName, muscleGroup: muscleGroup)
    }
}
