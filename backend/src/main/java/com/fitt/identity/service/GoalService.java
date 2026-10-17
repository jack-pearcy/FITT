package com.fitt.identity.service;

import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import com.fitt.identity.service.exception.GoalNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Owns goal data: reading it, writing it, and resolving the calorie goal.
// Pulled out of onboarding because changing a goal is a normal thing a user
// does any time, not a one time setup step. Biometrics and TDEE still remain in
// the picture here because an AUTO calorie goal is derived from them.
@Service
@Transactional(readOnly = true)
public class GoalService {

    private final UserGoalRepository goalRepository;
    private final UserBiometricsRepository biometricsRepository;
    private final TdeeService tdeeService;

    public GoalService(
            UserGoalRepository goalRepository,
            UserBiometricsRepository biometricsRepository,
            TdeeService tdeeService
    ) {
        this.goalRepository = goalRepository;
        this.biometricsRepository = biometricsRepository;
        this.tdeeService = tdeeService;
    }

    @Transactional
    public void saveGoals(Long userId, SaveGoalsRequest request) {
        // Goals need biometrics to exist so TDEE can be calculated in AUTO mode.
        UserBiometricsEntity biometrics = biometricsRepository
                .findByOwnerUserId(userId)
                .orElseThrow(() -> new BiometricsNotFoundException(
                        "biometrics not found for user " + userId
                ));

        Integer calorieGoal = resolveCalorieGoal(request, biometrics);

        goalRepository.findByOwnerUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.update(
                                request.goalDirection(),
                                request.targetWeightKg(),
                                request.timelineWeeks(),
                                request.calorieGoalMode(),
                                calorieGoal
                        ),
                        () -> goalRepository.save(new UserGoalEntity(
                                userId,
                                request.goalDirection(),
                                request.targetWeightKg(),
                                request.timelineWeeks(),
                                request.calorieGoalMode(),
                                calorieGoal
                        ))
                );
    }

    public GoalsResponse getGoals(Long userId) {
        UserGoalEntity goal = goalRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new GoalNotFoundException(
                        "goals not found for user " + userId
                ));

        return new GoalsResponse(
                goal.getGoalDirection(),
                goal.getTargetWeightKg(),
                goal.getTimelineWeeks(),
                goal.getCalorieGoalMode(),
                goal.getCalorieGoal()
        );
    }

    // Onboarding asks this to report whether the goals step is done, so it
    // never has to reach into the goal table itself.
    public boolean hasGoals(Long userId) {
        return goalRepository.existsByOwnerUserId(userId);
    }

    private Integer resolveCalorieGoal(
            SaveGoalsRequest request,
            UserBiometricsEntity biometrics
    ) {
        return switch (request.calorieGoalMode()) {
            case AUTO -> tdeeService.calculate(
                    biometrics.getHeightCm(),
                    biometrics.getWeightKg(),
                    biometrics.getDateOfBirth(),
                    biometrics.getBiologicalSex(),
                    biometrics.getActivityLevel()
            );
            case MANUAL -> {
                if (request.calorieGoal() == null) {
                    throw new IllegalArgumentException(
                            "calorie goal is required when mode is MANUAL"
                    );
                }
                yield request.calorieGoal();
            }
        };
    }
}