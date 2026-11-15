package com.fitt.shared.config;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.measurements.dto.LogBodyWeightRequest;
import com.fitt.workouts.dto.LogSetRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// The bounds on these DTOs mirror the database column sizes, so an oversized value
// is rejected as a 400 at the door instead of surfacing as a database error.
class RequestDtoValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static SaveBiometricsRequest biometrics(String height, String weight, LocalDate dob) {
        return new SaveBiometricsRequest(new BigDecimal(height), new BigDecimal(weight), dob,
                BiologicalSex.MALE, ActivityLevel.ACTIVE, UnitsPreference.METRIC);
    }

    @Test
    void logSet_realisticValues_pass() {
        assertThat(VALIDATOR.validate(new LogSetRequest(8, new BigDecimal("102.5"), new BigDecimal("8.5"))))
                .isEmpty();
        assertThat(VALIDATOR.validate(new LogSetRequest(12, null, null))).isEmpty();
    }

    @Test
    void logSet_valuesThatWouldOverflowColumns_fail() {
        assertThat(VALIDATOR.validate(new LogSetRequest(8, new BigDecimal("10000"), null))).hasSize(1);
        assertThat(VALIDATOR.validate(new LogSetRequest(8, new BigDecimal("-1"), null))).hasSize(1);
        assertThat(VALIDATOR.validate(new LogSetRequest(8, null, new BigDecimal("11")))).hasSize(1);
        assertThat(VALIDATOR.validate(new LogSetRequest(100000, null, null))).hasSize(1);
    }

    @Test
    void biometrics_validAndOutOfRange() {
        assertThat(VALIDATOR.validate(biometrics("180", "80", LocalDate.of(1990, 5, 1)))).isEmpty();
        assertThat(VALIDATOR.validate(biometrics("-5", "80", LocalDate.of(1990, 5, 1)))).isNotEmpty();
        assertThat(VALIDATOR.validate(biometrics("180", "5000", LocalDate.of(1990, 5, 1)))).isNotEmpty();
        assertThat(VALIDATOR.validate(biometrics("180", "80", LocalDate.now().plusDays(1)))).isNotEmpty();
    }

    @Test
    void goals_manualCalorieGoalMustBeSane() {
        assertThat(VALIDATOR.validate(new SaveGoalsRequest(
                GoalDirection.CUT, new BigDecimal("75"), 12, CalorieGoalMode.MANUAL, 2200))).isEmpty();
        assertThat(VALIDATOR.validate(new SaveGoalsRequest(
                GoalDirection.CUT, null, null, CalorieGoalMode.MANUAL, -300))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SaveGoalsRequest(
                GoalDirection.CUT, null, 0, CalorieGoalMode.AUTO, null))).isNotEmpty();
    }

    @Test
    void bodyWeight_upperBoundKeepsValueInsideColumn() {
        assertThat(VALIDATOR.validate(new LogBodyWeightRequest(LocalDate.now(), new BigDecimal("80.5"))))
                .isEmpty();
        assertThat(VALIDATOR.validate(new LogBodyWeightRequest(LocalDate.now(), new BigDecimal("99999"))))
                .isNotEmpty();
    }
}
