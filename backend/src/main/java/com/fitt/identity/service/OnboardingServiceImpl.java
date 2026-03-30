package com.fitt.identity.service;

import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.OnboardingStatusResponse;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.dto.TdeeResponse;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OnboardingServiceImpl implements OnboardingService {

    private final UserBiometricsRepository biometricsRepository;
    private final UserGoalRepository goalRepository;
    private final TdeeService tdeeService;

    public OnboardingServiceImpl(
            UserBiometricsRepository biometricsRepository,
            UserGoalRepository goalRepository,
            TdeeService tdeeService
    ) {
        this.biometricsRepository = biometricsRepository;
        this.goalRepository = goalRepository;
        this.tdeeService = tdeeService;
    }

    @Override
    @Transactional
    public void saveBiometrics(Long userId, SaveBiometricsRequest request) {
        biometricsRepository.findByOwnerUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.update(
                                request.heightCm(),
                                request.weightKg(),
                                request.dateOfBirth(),
                                request.biologicalSex(),
                                request.activityLevel(),
                                request.unitsPreference()
                        ),
                        () -> biometricsRepository.save(new UserBiometricsEntity(
                                userId,
                                request.heightCm(),
                                request.weightKg(),
                                request.dateOfBirth(),
                                request.biologicalSex(),
                                request.activityLevel(),
                                request.unitsPreference()
                        ))
                );
    }

    @Override
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

    @Override
    public OnboardingStatusResponse getStatus(Long userId) {
        boolean biometricsComplete = biometricsRepository.existsByOwnerUserId(userId);
        boolean goalsComplete = goalRepository.existsByOwnerUserId(userId);
        return new OnboardingStatusResponse(
                biometricsComplete,
                goalsComplete,
                biometricsComplete && goalsComplete
        );
    }

    @Override
    public TdeeResponse getTdee(Long userId) {
        UserBiometricsEntity biometrics = biometricsRepository
                .findByOwnerUserId(userId)
                .orElseThrow(() -> new BiometricsNotFoundException(
                        "biometrics not found for user " + userId
                ));

        int tdee = tdeeService.calculate(
                biometrics.getHeightCm(),
                biometrics.getWeightKg(),
                biometrics.getDateOfBirth(),
                biometrics.getBiologicalSex(),
                biometrics.getActivityLevel()
        );

        return new TdeeResponse(tdee);
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