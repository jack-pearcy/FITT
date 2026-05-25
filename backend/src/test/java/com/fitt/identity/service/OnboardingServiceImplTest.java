package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.OnboardingStatusResponse;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.dto.TdeeResponse;
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

// Service test for OnboardingServiceImpl. Repositories and TdeeService are mocked
// because they reach JPA and have their own logic. Service orchestration stays under test.
@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserBiometricsRepository biometricsRepository;

    @Mock
    private UserGoalRepository goalRepository;

    @Mock
    private TdeeService tdeeService;

    @InjectMocks
    private OnboardingServiceImpl service;

    @Test
    void saveBiometrics_noExistingBiometrics_savesNewEntity() {
        SaveBiometricsRequest request = new SaveBiometricsRequest(
                new BigDecimal("180"), new BigDecimal("80"),
                LocalDate.of(1995, 6, 15),
                BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE, UnitsPreference.METRIC);
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        service.saveBiometrics(USER_ID, request);

        ArgumentCaptor<UserBiometricsEntity> captor =
                ArgumentCaptor.forClass(UserBiometricsEntity.class);
        verify(biometricsRepository).save(captor.capture());

        UserBiometricsEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getHeightCm()).isEqualByComparingTo("180");
        assertThat(saved.getWeightKg()).isEqualByComparingTo("80");
        assertThat(saved.getBiologicalSex()).isEqualTo(BiologicalSex.MALE);
        assertThat(saved.getActivityLevel()).isEqualTo(ActivityLevel.MODERATELY_ACTIVE);
    }

    @Test
    void saveBiometrics_existingBiometrics_updatesInPlaceAndDoesNotCallSave() {
        UserBiometricsEntity existing = new UserBiometricsEntity(
                USER_ID, new BigDecimal("170"), new BigDecimal("70"),
                LocalDate.of(1990, 1, 1),
                BiologicalSex.FEMALE, ActivityLevel.SEDENTARY, UnitsPreference.METRIC);
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(existing));

        SaveBiometricsRequest request = new SaveBiometricsRequest(
                new BigDecimal("172"), new BigDecimal("68"),
                LocalDate.of(1990, 1, 1),
                BiologicalSex.FEMALE, ActivityLevel.ACTIVE, UnitsPreference.IMPERIAL);

        service.saveBiometrics(USER_ID, request);

        // Existing entity mutated in place via update(). Hibernate dirty checking persists.
        assertThat(existing.getHeightCm()).isEqualByComparingTo("172");
        assertThat(existing.getWeightKg()).isEqualByComparingTo("68");
        assertThat(existing.getActivityLevel()).isEqualTo(ActivityLevel.ACTIVE);
        verify(biometricsRepository, never()).save(any(UserBiometricsEntity.class));
    }

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
    }

    @Test
    void saveGoals_autoMode_noExistingGoals_savesNewWithTdeeAsCalorieGoal() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(2500);
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
        // AUTO mode pulls calorie goal from TdeeService output.
        assertThat(saved.getCalorieGoal()).isEqualTo(2500);
    }

    @Test
    void saveGoals_autoMode_existingGoals_updatesInPlaceAndDoesNotCallSave() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        UserGoalEntity existing = new UserGoalEntity(
                USER_ID, GoalDirection.MAINTAIN, null, null,
                CalorieGoalMode.MANUAL, 2000);
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(2700);
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(existing));

        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.CUT, new BigDecimal("75"), 8,
                CalorieGoalMode.AUTO, null);

        service.saveGoals(USER_ID, request);

        assertThat(existing.getGoalDirection()).isEqualTo(GoalDirection.CUT);
        assertThat(existing.getCalorieGoalMode()).isEqualTo(CalorieGoalMode.AUTO);
        assertThat(existing.getCalorieGoal()).isEqualTo(2700);
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
        // MANUAL mode uses the request value directly. TdeeService should never be called.
        assertThat(captor.getValue().getCalorieGoal()).isEqualTo(2300);
        verifyNoInteractions(tdeeService);
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

    @Test
    void getStatus_bothBiometricsAndGoalsExist_returnsAllComplete() {
        when(biometricsRepository.existsByOwnerUserId(USER_ID)).thenReturn(true);
        when(goalRepository.existsByOwnerUserId(USER_ID)).thenReturn(true);

        OnboardingStatusResponse status = service.getStatus(USER_ID);

        assertThat(status.biometricsComplete()).isTrue();
        assertThat(status.goalsComplete()).isTrue();
        assertThat(status.onboardingComplete()).isTrue();
    }

    @Test
    void getStatus_onlyBiometricsExists_returnsOnboardingNotComplete() {
        when(biometricsRepository.existsByOwnerUserId(USER_ID)).thenReturn(true);
        when(goalRepository.existsByOwnerUserId(USER_ID)).thenReturn(false);

        OnboardingStatusResponse status = service.getStatus(USER_ID);

        // onboardingComplete is the AND of the two booleans. One false means overall false.
        assertThat(status.biometricsComplete()).isTrue();
        assertThat(status.goalsComplete()).isFalse();
        assertThat(status.onboardingComplete()).isFalse();
    }

    @Test
    void getTdee_existingBiometrics_delegatesToTdeeService() {
        UserBiometricsEntity biometrics = sampleBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(2400);

        TdeeResponse response = service.getTdee(USER_ID);

        assertThat(response.tdee()).isEqualTo(2400);
    }

    @Test
    void getTdee_noBiometrics_throwsBiometricsNotFoundException() {
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTdee(USER_ID))
                .isInstanceOf(BiometricsNotFoundException.class)
                .hasMessageContaining(String.valueOf(USER_ID));

        verifyNoInteractions(tdeeService);
    }

    private UserBiometricsEntity sampleBiometrics() {
        return new UserBiometricsEntity(
                USER_ID, new BigDecimal("180"), new BigDecimal("80"),
                LocalDate.of(1995, 6, 15),
                BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE, UnitsPreference.METRIC);
    }
}