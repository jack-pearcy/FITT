package com.fitt.workouts.service;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;
import com.fitt.workouts.persistence.entity.UserProgressionPreferenceEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.progression.ProgressionStrategy;
import com.fitt.workouts.progression.ProgressionStrategyResolver;
import com.fitt.workouts.progression.ProgressionSuggestion;
import com.fitt.workouts.repository.UserProgressionPreferenceRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProgressionServiceImpl implements ProgressionService {

    private final WorkoutSessionRepository sessionRepository;
    private final UserProgressionPreferenceRepository preferenceRepository;
    private final ProgressionStrategyResolver strategyResolver;

    // How many past sessions the algorithm analyzes
    private static final int HISTORY_DEPTH = 5;

    public ProgressionServiceImpl(WorkoutSessionRepository sessionRepository,
                                  UserProgressionPreferenceRepository preferenceRepository,
                                  ProgressionStrategyResolver strategyResolver) {
        this.sessionRepository = sessionRepository;
        this.preferenceRepository = preferenceRepository;
        this.strategyResolver = strategyResolver;
    }

    @Override
    public ProgressionSuggestionResponse getSuggestion(Long ownerUserId, Long exerciseId) {
        ProgressionModel model = getPreference(ownerUserId);
        ProgressionStrategy strategy = strategyResolver.resolve(model);

        List<WorkoutSessionEntity> recentSessions = sessionRepository
                .findCompletedSessionsByExercise(ownerUserId, exerciseId, PageRequest.of(0, HISTORY_DEPTH));

        ProgressionSuggestion suggestion = strategy.suggest(exerciseId, recentSessions);
        return ProgressionSuggestionResponse.from(suggestion, model);
    }

    @Override
    public ProgressionModel getPreference(Long ownerUserId) {
        return preferenceRepository.findByOwnerUserId(ownerUserId)
                .map(UserProgressionPreferenceEntity::getProgressionModel)
                .orElse(ProgressionModel.DOUBLE_PROGRESSION);
    }

    @Override
    @Transactional
    public void updatePreference(Long ownerUserId, ProgressionModel model) {
        UserProgressionPreferenceEntity pref = preferenceRepository.findByOwnerUserId(ownerUserId)
                .orElse(new UserProgressionPreferenceEntity(ownerUserId, model));
        pref.setProgressionModel(model);
        preferenceRepository.save(pref);
    }
}
