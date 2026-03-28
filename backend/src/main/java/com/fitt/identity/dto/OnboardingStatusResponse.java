package com.fitt.identity.dto;

public record OnboardingStatusResponse(
        boolean biometricsComplete,
        boolean goalsComplete,
        boolean onboardingComplete
) {}