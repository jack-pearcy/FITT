package com.fitt.identity.dto;

import com.fitt.identity.domain.UnitsPreference;

// unitsPreference is null until biometrics are saved. A client reads it here because
// no other route returns the biometrics, and it needs the units on every launch.
public record OnboardingStatusResponse(
        boolean biometricsComplete,
        boolean goalsComplete,
        boolean onboardingComplete,
        UnitsPreference unitsPreference
) {}