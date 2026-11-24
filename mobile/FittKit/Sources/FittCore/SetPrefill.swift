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
    /// 3. the newest finished workout with the exercise: the set with the same
    ///    number, else its last set;
    /// 4. nothing, so the fields start empty.
    ///
    /// Weights arrive in kilograms and are shown in the user's unit. A weight
    /// from the suggestion or from history is rounded to a plate step, so a
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
        return fromHistory(exerciseId: exerciseId, setNumber: 1, history: history, units: units)
    }

    /// The set with this number from the newest finished workout that has the
    /// exercise, else that workout's last set. Empty when there is none.
    public static func fromHistory(
        exerciseId: Int64,
        setNumber: Int,
        history: [WorkoutSession],
        units: UnitsPreference
    ) -> SetDraft {
        guard let last = LastTime.find(exerciseId: exerciseId, in: history),
              let set = last.sets.first(where: { $0.setNumber == setNumber }) ?? last.sets.last
        else { return SetDraft() }
        return SetDraft(
            weight: set.weight.map { platedWeight($0, units: units) } ?? "",
            reps: set.reps.map(String.init) ?? "")
    }

    private static func platedWeight(_ kilograms: Decimal, units: UnitsPreference) -> String {
        PlateStep.plain(PlateStep(units: units).rounded(units.shown(kilograms: kilograms)))
    }
}
