package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.dto.OnboardingStatusResponse;
import com.fitt.identity.dto.SaveBiometricsRequest;
import com.fitt.identity.dto.TdeeResponse;
import com.fitt.identity.repository.UserBiometricsRepository;
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

// Service test for OnboardingServiceImpl. The biometrics repository, GoalService,
// and TdeeService are mocked because they reach JPA or own their logic. Service
// orchestration stays under test.
@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserBiometricsRepository biometricsRepository;

    @Mock
    private GoalService goalService;

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
    void getStatus_bothBiometricsAndGoalsExist_returnsAllComplete() {
        when(biometricsRepository.existsByOwnerUserId(USER_ID)).thenReturn(true);
        when(goalService.hasGoals(USER_ID)).thenReturn(true);

        OnboardingStatusResponse status = service.getStatus(USER_ID);

        assertThat(status.biometricsComplete()).isTrue();
        assertThat(status.goalsComplete()).isTrue();
        assertThat(status.onboardingComplete()).isTrue();
    }

    @Test
    void getStatus_onlyBiometricsExists_returnsOnboardingNotComplete() {
        when(biometricsRepository.existsByOwnerUserId(USER_ID)).thenReturn(true);
        when(goalService.hasGoals(USER_ID)).thenReturn(false);

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