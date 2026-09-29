package com.fitt.workouts.service;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;
import com.fitt.workouts.persistence.entity.UserProgressionPreferenceEntity;
import com.fitt.workouts.persistence.entity.WorkoutPlanEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.progression.ProgressionContext;
import com.fitt.workouts.progression.ProgressionStrategy;
import com.fitt.workouts.progression.ProgressionStrategyResolver;
import com.fitt.workouts.progression.ProgressionSuggestion;
import com.fitt.workouts.repository.UserProgressionPreferenceRepository;
import com.fitt.workouts.repository.WorkoutPlanRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProgressionServiceImpl implements ProgressionService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutPlanRepository planRepository;
    private final UserProgressionPreferenceRepository preferenceRepository;
    private final ProgressionStrategyResolver strategyResolver;

    // How many past sessions the algorithm analyzes for weight and rep data
    private static final int HISTORY_DEPTH = 5;

    public ProgressionServiceImpl(WorkoutSessionRepository sessionRepository,
                                  WorkoutPlanRepository planRepository,
                                  UserProgressionPreferenceRepository preferenceRepository,
                                  ProgressionStrategyResolver strategyResolver) {
        this.sessionRepository = sessionRepository;
        this.planRepository = planRepository;
        this.preferenceRepository = preferenceRepository;
        this.strategyResolver = strategyResolver;
    }

    @Override
    public ProgressionSuggestionResponse getSuggestion(Long ownerUserId, Long exerciseId) {
        ProgressionModel model = getPreference(ownerUserId);
        ProgressionStrategy strategy = strategyResolver.resolve(model);

        List<WorkoutSessionEntity> recentSessions = sessionRepository
                .findCompletedSessionsByExercise(ownerUserId, exerciseId, PageRequest.of(0, HISTORY_DEPTH));

        // Program level inputs for cycle-based strategies like linear periodization.
        // Pulled from the user's active plan: the true count of completed sessions
        // against it (which drives the week in the cycle) and how many days are in one
        // full rotation of the split. No active plan means no program context, so both
        // stay zero, which the strategies read as the start of a cycle.
        long completedSessionCount = 0;
        int daysInRotation = 0;
        Optional<WorkoutPlanEntity> activePlan = planRepository.findByOwnerUserIdAndIsActiveTrue(ownerUserId);
        if (activePlan.isPresent()) {
            WorkoutPlanEntity plan = activePlan.get();
            completedSessionCount = sessionRepository
                    .countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(ownerUserId, plan.getId());
            daysInRotation = plan.getDays().size();
        }

        ProgressionContext context = new ProgressionContext(
                exerciseId, recentSessions, completedSessionCount, daysInRotation);

        ProgressionSuggestion suggestion = strategy.suggest(context);
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