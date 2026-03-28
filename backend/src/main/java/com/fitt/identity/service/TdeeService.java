package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

@Service
public class TdeeService {

    // Mifflin-St Jeor (1990). Am J Clin Nutr 51:241-7.
    // Male:   (10 * kg) + (6.25 * cm) - (5 * age) + 5
    // Female: (10 * kg) + (6.25 * cm) - (5 * age) - 161
    public int calculate(
            BigDecimal heightCm,
            BigDecimal weightKg,
            LocalDate dateOfBirth,
            BiologicalSex biologicalSex,
            ActivityLevel activityLevel
    ) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        double h = heightCm.doubleValue();
        double w = weightKg.doubleValue();

        double sexOffset = biologicalSex == BiologicalSex.MALE ? 5.0 : -161.0;
        double bmr = (10.0 * w) + (6.25 * h) - (5.0 * age) + sexOffset;
        double tdee = bmr * activityMultiplier(activityLevel);

        return (int) Math.round(tdee);
    }

    private double activityMultiplier(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY         -> 1.2;
            case LIGHTLY_ACTIVE    -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case ACTIVE            -> 1.725;
            case VERY_ACTIVE       -> 1.9;
        };
    }
}