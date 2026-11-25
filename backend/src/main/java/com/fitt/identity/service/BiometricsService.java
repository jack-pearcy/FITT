package com.fitt.identity.service;

import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.dto.BiometricsResponse;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads biometrics and changes units after onboarding. Saving the full set still goes
// through OnboardingService.
@Service
@Transactional(readOnly = true)
public class BiometricsService {

    private final UserBiometricsRepository biometricsRepository;

    public BiometricsService(UserBiometricsRepository biometricsRepository) {
        this.biometricsRepository = biometricsRepository;
    }

    public BiometricsResponse getBiometrics(Long userId) {
        UserBiometricsEntity biometrics = find(userId);
        return new BiometricsResponse(
                biometrics.getHeightCm(),
                biometrics.getWeightKg(),
                biometrics.getDateOfBirth(),
                biometrics.getBiologicalSex(),
                biometrics.getActivityLevel(),
                biometrics.getUnitsPreference(),
                biometrics.getUpdatedAt()
        );
    }

    @Transactional
    public void changeUnits(Long userId, UnitsPreference unitsPreference) {
        find(userId).changeUnits(unitsPreference);
    }

    private UserBiometricsEntity find(Long userId) {
        return biometricsRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new BiometricsNotFoundException(
                        "biometrics not found for user " + userId
                ));
    }
}
