package com.fitt.workouts.domain;

// The three evidence backed progression strategies FITT supports.
// Each value maps to a @Component-named ProgressionStrategy at runtime.
public enum ProgressionModel {
    DOUBLE_PROGRESSION,
    RPE_AUTOREGULATION,
    LINEAR_PERIODIZATION
}
