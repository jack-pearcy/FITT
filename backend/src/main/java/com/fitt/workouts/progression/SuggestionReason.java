package com.fitt.workouts.progression;

// Why a strategy made its suggestion. Each value matches one branch in a strategy,
// so a client can build its own text and show weights in its own units.
public enum SuggestionReason {

    // No finished session holds this exercise yet. All strategies.
    NO_HISTORY,

    // The latest session has no set with both reps and weight. Double and linear.
    NO_SETS,

    // The latest session has no set with both RPE and weight. RPE autoregulation.
    NO_RPE_DATA,

    // Top of the rep range on every set for enough sessions in a row. Double.
    INCREASE_WEIGHT,

    // Keep the weight and add reps toward the top of the range. Double.
    ADD_REPS,

    // Average RPE fell below the target zone, so the weight goes up. RPE autoregulation.
    RPE_LOW,

    // Average RPE rose above the target zone, so the weight goes down. RPE autoregulation.
    RPE_HIGH,

    // Average RPE sat inside the target zone, so the weight stays. RPE autoregulation.
    HOLD,

    // A loading week of the cycle, with a heavier weight and fewer reps. Linear.
    WEEK_STEP,

    // The last week of the cycle, with a lighter weight and more reps. Linear.
    DELOAD
}
