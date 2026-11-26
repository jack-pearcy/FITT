import Foundation

extension SuggestionReason {
    /// False for the reasons given when the server had no sets to work from.
    var namesWeight: Bool {
        switch self {
        case .noHistory, .noSets, .noRpeData: false
        case .increaseWeight, .addReps, .rpeLow, .rpeHigh, .hold, .weekStep, .deload: true
        }
    }
}

extension ProgressionSuggestion {
    /// Why the server suggests what it does, with every weight in the unit the
    /// user works in. The server's own text has its weights in kilograms, so it
    /// is used only when there is nothing to write from: an older server, a
    /// code added after this app was built, or a weight reason with no weight.
    public func reasonText(units: UnitsPreference) -> String {
        guard let reasonCode, hasTarget || !reasonCode.namesWeight else { return reasoning }

        let reps = targetReps ?? suggestedReps
        let forReps = reps.map { " for \($0) reps" } ?? ""
        let weight = units.weightText(kilograms: suggestedWeight)
        let from = previousWeightKg.map { "from \(units.weightText(kilograms: $0)) " } ?? ""
        let rpe = averageRpe.map { " of \($0.formatted())" } ?? ""

        switch reasonCode {
        case .noHistory:
            return "No history for this exercise yet. Start with a weight you can control\(forReps)."
        case .noSets:
            return "Your last workout has no set with reps and a weight for this exercise."
        case .noRpeData:
            return "Your last workout has no set with an RPE for this exercise. Log RPE to get a weight."
        case .increaseWeight:
            let restart = reps.map { " and start again at \($0) reps" } ?? ""
            return "You reached the top of the rep range on every set. Go up \(from)to \(weight)\(restart)."
        case .addReps:
            let goal = reps.map { "work up to \($0) reps" } ?? "add reps"
            return "Stay at \(weight) and \(goal) on every set before adding weight."
        case .rpeLow:
            return "Your average RPE\(rpe) was below the target zone. Go up \(from)to \(weight)."
        case .rpeHigh:
            return "Your average RPE\(rpe) was above the target zone. Drop \(from)to \(weight)."
        case .hold:
            return "Your average RPE\(rpe) was inside the target zone. Stay at \(weight)."
        case .weekStep:
            let week = weekInCycle.map { "Week \($0) of the cycle. " } ?? ""
            return "\(week)Go up \(from)to \(weight)\(forReps)."
        case .deload:
            return "Deload week. Drop \(from)to \(weight)\(forReps) to recover before the next cycle."
        }
    }
}
