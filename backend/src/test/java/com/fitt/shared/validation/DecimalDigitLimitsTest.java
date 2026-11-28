package com.fitt.shared.validation;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.measurements.dto.LogBodyWeightRequest;
import com.fitt.workouts.dto.LogSetRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Every decimal a client can send is checked against the scale of the column it
// is stored in. A number with more decimal places is refused, because the
// database would round it and the value saved would not be the value sent.
class DecimalDigitLimitsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void logSet_weightAndRpeAtTheColumnScale_areAccepted() {
        assertThat(messages(new LogSetRequest(8, new BigDecimal("102.06"), new BigDecimal("8.5")))).isEmpty();
        assertThat(messages(new LogSetRequest(8, new BigDecimal("9999.99"), new BigDecimal("10")))).isEmpty();
        assertThat(messages(new LogSetRequest(12, null, null))).isEmpty();
    }

    @Test
    void logSet_weightWithThreeDecimalPlaces_isRefused() {
        assertThat(messages(new LogSetRequest(8, new BigDecimal("80.255"), null)))
                .containsExactly("Weight can have at most 2 decimal places");
    }

    @Test
    void logSet_rpeWithTwoDecimalPlaces_isRefused() {
        assertThat(messages(new LogSetRequest(8, null, new BigDecimal("8.25"))))
                .containsExactly("RPE can have at most 1 decimal place");
    }

    @Test
    void logBodyWeight_twoDecimalPlaces_isAccepted_threeIsRefused() {
        LocalDate day = LocalDate.of(2026, 5, 25);
        assertThat(messages(new LogBodyWeightRequest(day, new BigDecimal("81.83")))).isEmpty();
        assertThat(messages(new LogBodyWeightRequest(day, new BigDecimal("81.828"))))
                .containsExactly("Weight can have at most 2 decimal places");
    }

    @Test
    void saveBiometrics_twoDecimalPlaces_isAccepted_threeIsRefused() {
        assertThat(messages(biometrics("177.80", "81.65"))).isEmpty();
        assertThat(messages(biometrics("177.801", "81.65")))
                .containsExactly("Height can have at most 2 decimal places");
        assertThat(messages(biometrics("177.80", "81.647")))
                .containsExactly("Weight can have at most 2 decimal places");
    }

    @Test
    void saveGoals_targetWeightWithThreeDecimalPlaces_isRefused() {
        assertThat(messages(goals("97.52"))).isEmpty();
        assertThat(messages(goals(null))).isEmpty();
        assertThat(messages(goals("97.522")))
                .containsExactly("Target weight can have at most 2 decimal places");
    }

    private static SaveBiometricsRequest biometrics(String heightCm, String weightKg) {
        return new SaveBiometricsRequest(
                new BigDecimal(heightCm), new BigDecimal(weightKg), LocalDate.of(1994, 3, 7),
                BiologicalSex.MALE, ActivityLevel.LIGHTLY_ACTIVE, UnitsPreference.IMPERIAL);
    }

    private static SaveGoalsRequest goals(String targetWeightKg) {
        return new SaveGoalsRequest(
                GoalDirection.BULK, targetWeightKg == null ? null : new BigDecimal(targetWeightKg),
                null, CalorieGoalMode.AUTO, null, null, null);
    }

    private static <T> Set<String> messages(T request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
