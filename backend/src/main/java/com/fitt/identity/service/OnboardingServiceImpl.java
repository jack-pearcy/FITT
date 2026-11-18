package com.fitt.identity.service;

import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.dto.*;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OnboardingServiceImpl implements OnboardingService {

    private final UserBiometricsRepository biometricsRepository;
    private final GoalService goalService;
    private final TdeeService tdeeService;

    public OnboardingServiceImpl(
            UserBiometricsRepository biometricsRepository,
            GoalService goalService,
            TdeeService tdeeService
    ) {
        this.biometricsRepository = biometricsRepository;
        this.goalService = goalService;
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
    public OnboardingStatusResponse getStatus(Long userId) {
        Optional<UserBiometricsEntity> biometrics = biometricsRepository.findByOwnerUserId(userId);
        boolean biometricsComplete = biometrics.isPresent();
        // Goals live in GoalService now, so ask it instead of reaching into the goal table.
        boolean goalsComplete = goalService.hasGoals(userId);
        return new OnboardingStatusResponse(
                biometricsComplete,
                goalsComplete,
                biometricsComplete && goalsComplete,
                biometrics.map(UserBiometricsEntity::getUnitsPreference).orElse(null)
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
}