package com.fitt.workouts.progression;


// Strategy interface for the progression engine.
// Each implementation encapsulates one evidence-backed progression algorithm.
// Adding a new model = one new class implementing this, no changes to existing code.
public interface ProgressionStrategy {

    // Analyze progression context and return a suggestion
    ProgressionSuggestion suggest(ProgressionContext context);
}
