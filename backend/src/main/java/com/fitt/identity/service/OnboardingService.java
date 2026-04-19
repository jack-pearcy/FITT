package com.fitt.identity.service;

import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.dto.OnboardingStatusResponse;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.dto.TdeeResponse;

public interface OnboardingService {

    void saveBiometrics(Long userId, SaveBiometricsRequest request);
    void saveGoals(Long userId, SaveGoalsRequest request);
    OnboardingStatusResponse getStatus(Long userId);
    TdeeResponse getTdee(Long userId);
    GoalsResponse getGoals(Long userId);
}