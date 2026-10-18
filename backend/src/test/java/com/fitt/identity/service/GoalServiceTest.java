package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Service test for GoalService. The repositories, TdeeService, and
// CalorieGoalCalculator are mocked because they reach JPA or own their own
// logic. The goal write path and the AUTO and MANUAL calorie resolution stay
// under test. AUTO now runs maintenance through the calculator, so these tests
// assert the adjusted number, not raw TDEE.
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserGoalRepository goalRepository;

    @Mock
    private UserBiometricsRepository biometricsRepository;

    @Mock
    private TdeeService tdeeService;

    @Mock
    private CalorieGoalCalculator calorieGoalCalculator;

    @InjectMocks
    private GoalService service;

    @Test
    void saveGoals_noBiometrics_throwsBiometricsNotFoundException() {
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());
        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.BULK, new BigDecimal("85"), 12,
                CalorieGoalMode.AUTO, null);

        assertThatThrownBy(() -> service.saveGoals(USER_ID, request))
                .isInstanceOf(BiometricsNotFoundException.class)
                .hasMessageContaining(String.valueOf(USER_ID));

        verifyNoInteractions(goalRepository);
        verifyNoInteractions(tdeeService);
        verifyNoInteractions(calorieGoalCalculator);
    }

    @Test
    void saveGoals_autoModeBulk_savesNewWithDirectionAdjustedGoal() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(2500);
        when(calorieGoalCalculator.adjustForGoal(2500, GoalDirection.BULK)).thenReturn(2875);
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.BULK, new BigDecimal("85"), 12,
                CalorieGoalMode.AUTO, null);

        service.saveGoals(USER_ID, request);

        ArgumentCaptor<UserGoalEntity> captor = ArgumentCaptor.forClass(UserGoalEntity.class);
        verify(goalRepository).save(captor.capture());
        UserGoalEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getGoalDirection()).isEqualTo(GoalDirection.BULK);
        assertThat(saved.getCalorieGoalMode()).isEqualTo(CalorieGoalMode.AUTO);
        // AUTO runs maintenance through the calculator, which applies the bulk surplus.
        assertThat(saved.getCalorieGoal()).isEqualTo(2875);
    }

    @Test
    void saveGoals_autoMode_existingGoals_updatesInPlaceAndDoesNotCallSave() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        UserGoalEntity existing = new UserGoalEntity(
                USER_ID, GoalDirection.MAINTAIN, null, null,
                CalorieGoalMode.MANUAL, 2000);
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(2700);
        when(calorieGoalCalculator.adjustForGoal(2700, GoalDirection.CUT)).thenReturn(2160);
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(existing));

        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.CUT, new BigDecimal("75"), 8,
                CalorieGoalMode.AUTO, null);

        service.saveGoals(USER_ID, request);

        assertThat(existing.getGoalDirection()).isEqualTo(GoalDirection.CUT);
        assertThat(existing.getCalorieGoalMode()).isEqualTo(CalorieGoalMode.AUTO);
        assertThat(existing.getCalorieGoal()).isEqualTo(2160);
        verify(goalRepository, never()).save(any(UserGoalEntity.class));
    }

    @Test
    void saveGoals_manualMode_validCalorieGoal_savesManualValueWithoutCallingTdee() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.MAINTAIN, null, null,
                CalorieGoalMode.MANUAL, 2300);

        service.saveGoals(USER_ID, request);

        ArgumentCaptor<UserGoalEntity> captor = ArgumentCaptor.forClass(UserGoalEntity.class);
        verify(goalRepository).save(captor.capture());
        // MANUAL uses the request value directly, so neither TdeeService nor the calculator runs.
        assertThat(captor.getValue().getCalorieGoal()).isEqualTo(2300);
        verifyNoInteractions(tdeeService);
        verifyNoInteractions(calorieGoalCalculator);
    }

    @Test
    void saveGoals_manualMode_nullCalorieGoal_throwsIllegalArgumentException() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));

        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.MAINTAIN, null, null,
                CalorieGoalMode.MANUAL, null);

        assertThatThrownBy(() -> service.saveGoals(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANUAL");

        verify(goalRepository, never()).save(any(UserGoalEntity.class));
    }

    private UserBiometricsEntity sampleBiometrics() {
        return new UserBiometricsEntity(
                USER_ID, new BigDecimal("180"), new BigDecimal("80"),
                LocalDate.of(1995, 6, 15),
                BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE, UnitsPreference.METRIC);
    }
}