import Foundation

/// What the weight and reps fields hold before the user types, so the next set
/// can be logged with one tap. All of it is worked out on the device from what
/// the app already has: the workout in progress, the suggestion, and the
/// finished workouts.
public enum SetPrefill {
    /// The set to fill in next for one exercise, from the first of these that
    /// has something:
    /// 1. the newest set logged for it in this workout, repeated as it is;
    /// 2. the suggestion, when it has a target;
    /// 3. the first set of the newest finished workout with the exercise;
    /// 4. nothing, so the fields start empty.
    ///
    /// Weights arrive in kilograms and are shown in the user's unit. A weight
    /// the user lifted, today or last time, is shown as it was, so a 24 kg
    /// dumbbell stays 24. Only the suggestion is rounded to a plate step, so a
    /// pound user sees 225 rather than 225.4.
    public static func draft(
        exerciseId: Int64,
        loggedToday: [SetEntry],
        suggestion: ProgressionSuggestion?,
        history: [WorkoutSession],
        units: UnitsPreference
    ) -> SetDraft {
        if let newest = loggedToday.max(by: { $0.setNumber < $1.setNumber }) {
            return SetDraft(
                weight: newest.weight.map { PlateStep.plain(units.shown(kilograms: $0)) } ?? "",
                reps: newest.reps.map(String.init) ?? "")
        }
        if let suggestion, suggestion.hasTarget {
            return SetDraft(
                weight: platedWeight(suggestion.suggestedWeight, units: units),
                reps: suggestion.suggestedReps.map(String.init) ?? "")
        }
        return fromHistory(exerciseId: exerciseId, history: history, units: units)
    }

    /// The first set of the newest finished workout that has the exercise, in
    /// the user's unit and not rounded. Empty when there is none.
    private static func fromHistory(
        exerciseId: Int64,
        history: [WorkoutSession],
        units: UnitsPreference
    ) -> SetDraft {
        guard let last = LastTime.find(exerciseId: exerciseId, in: history),
              let set = last.sets.first
        else { return SetDraft() }
        return SetDraft(
            weight: set.weight.map { PlateStep.plain(units.shown(kilograms: $0)) } ?? "",
            reps: set.reps.map(String.init) ?? "")
    }

    private static func platedWeight(_ kilograms: Decimal, units: UnitsPreference) -> String {
        PlateStep.plain(PlateStep(units: units).rounded(units.shown(kilograms: kilograms)))
    }
}
