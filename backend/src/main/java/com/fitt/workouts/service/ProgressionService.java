package com.fitt.workouts.service;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;

public interface ProgressionService {

    ProgressionSuggestionResponse getSuggestion(Long ownerUserId, Long exerciseId);

    ProgressionModel getPreference(Long ownerUserId);

    void updatePreference(Long ownerUserId, ProgressionModel model);
}