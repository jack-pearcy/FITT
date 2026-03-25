package com.fitt.workouts.dto;

import com.fitt.workouts.domain.ProgressionModel;
import jakarta.validation.constraints.NotNull;

public record UpdateProgressionPreferenceRequest(
        @NotNull(message = "Progression model is required")
        ProgressionModel progressionModel
) {}
