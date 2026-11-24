package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.dto.BiometricsResponse;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Service test for BiometricsService with the repository mocked.
@ExtendWith(MockitoExtension.class)
class BiometricsServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserBiometricsRepository biometricsRepository;

    @InjectMocks
    private BiometricsService service;

    private UserBiometricsEntity savedBiometrics() {
        return new UserBiometricsEntity(
                USER_ID, new BigDecimal("175.00"), new BigDecimal("72.50"),
                LocalDate.of(1990, 3, 2),
                BiologicalSex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE, UnitsPreference.METRIC);
    }

    @Test
    void getBiometrics_saved_mapsEveryField() {
        UserBiometricsEntity entity = savedBiometrics();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(entity));

        BiometricsResponse response = service.getBiometrics(USER_ID);

        assertThat(response.heightCm()).isEqualByComparingTo("175");
        assertThat(response.weightKg()).isEqualByComparingTo("72.5");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1990, 3, 2));
        assertThat(response.biologicalSex()).isEqualTo(BiologicalSex.FEMALE);
        assertThat(response.activityLevel()).isEqualTo(ActivityLevel.LIGHTLY_ACTIVE);
        assertThat(response.unitsPreference()).isEqualTo(UnitsPreference.METRIC);
        assertThat(response.updatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void getBiometrics_noneSaved_throwsNotFound() {
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBiometrics(USER_ID))
                .isInstanceOf(BiometricsNotFoundException.class);
    }

    @Test
    void changeUnits_saved_changesUnitsOnlyAndTouchesUpdatedAt() {
        UserBiometricsEntity entity = savedBiometrics();
        Instant before = entity.getUpdatedAt();
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(entity));

        service.changeUnits(USER_ID, UnitsPreference.IMPERIAL);

        assertThat(entity.getUnitsPreference()).isEqualTo(UnitsPreference.IMPERIAL);
        assertThat(entity.getHeightCm()).isEqualByComparingTo("175");
        assertThat(entity.getWeightKg()).isEqualByComparingTo("72.5");
        assertThat(entity.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 3, 2));
        assertThat(entity.getBiologicalSex()).isEqualTo(BiologicalSex.FEMALE);
        assertThat(entity.getActivityLevel()).isEqualTo(ActivityLevel.LIGHTLY_ACTIVE);
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void changeUnits_noneSaved_throwsNotFound() {
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeUnits(USER_ID, UnitsPreference.IMPERIAL))
                .isInstanceOf(BiometricsNotFoundException.class);
    }
}
