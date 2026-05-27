package com.fitt.workouts.service;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;
import com.fitt.workouts.persistence.entity.UserProgressionPreferenceEntity;
import com.fitt.workouts.progression.ProgressionStrategy;
import com.fitt.workouts.progression.ProgressionStrategyResolver;
import com.fitt.workouts.progression.ProgressionSuggestion;
import com.fitt.workouts.repository.UserProgressionPreferenceRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Service layer test. Repositories and the resolver are mocked because they
// reach JPA and Spring's bean wiring. Service logic stays under test.
@ExtendWith(MockitoExtension.class)
class ProgressionServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long EXERCISE_ID = 100L;

    @Mock
    private WorkoutSessionRepository sessionRepository;

    @Mock
    private UserProgressionPreferenceRepository preferenceRepository;

    @Mock
    private ProgressionStrategyResolver strategyResolver;

    @InjectMocks
    private ProgressionServiceImpl service;

    @Test
    void getSuggestion_buildsResponseFromResolvedStrategyAndRecentSessions() {
        UserProgressionPreferenceEntity preference =
                new UserProgressionPreferenceEntity(USER_ID, ProgressionModel.LINEAR_PERIODIZATION);
        ProgressionStrategy strategy = mock(ProgressionStrategy.class);
        ProgressionSuggestion suggestion = new ProgressionSuggestion(
                EXERCISE_ID, new BigDecimal("102.5"), 8, 3, "test reasoning");

        when(preferenceRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(preference));
        when(strategyResolver.resolve(ProgressionModel.LINEAR_PERIODIZATION)).thenReturn(strategy);
        when(sessionRepository.findCompletedSessionsByExercise(eq(USER_ID), eq(EXERCISE_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(strategy.suggest(EXERCISE_ID, List.of())).thenReturn(suggestion);

        ProgressionSuggestionResponse response = service.getSuggestion(USER_ID, EXERCISE_ID);

        assertThat(response.exerciseId()).isEqualTo(EXERCISE_ID);
        assertThat(response.modelUsed()).isEqualTo(ProgressionModel.LINEAR_PERIODIZATION);
        assertThat(response.suggestedWeight()).isEqualTo("102.5");
        assertThat(response.suggestedReps()).isEqualTo(8);
        assertThat(response.suggestedSets()).isEqualTo(3);
        assertThat(response.reasoning()).isEqualTo("test reasoning");
    }

    @Test
    void getPreference_userHasSavedPreference_returnsThatModel() {
        UserProgressionPreferenceEntity preference =
                new UserProgressionPreferenceEntity(USER_ID, ProgressionModel.RPE_AUTOREGULATION);
        when(preferenceRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(preference));

        ProgressionModel model = service.getPreference(USER_ID);

        assertThat(model).isEqualTo(ProgressionModel.RPE_AUTOREGULATION);
    }

    @Test
    void getPreference_userHasNoSavedPreference_returnsDoubleProgressionDefault() {
        when(preferenceRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        ProgressionModel model = service.getPreference(USER_ID);

        assertThat(model).isEqualTo(ProgressionModel.DOUBLE_PROGRESSION);
    }

    @Test
    void updatePreference_existingPreference_mutatesAndSaves() {
        UserProgressionPreferenceEntity existing =
                new UserProgressionPreferenceEntity(USER_ID, ProgressionModel.DOUBLE_PROGRESSION);
        when(preferenceRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(existing));

        service.updatePreference(USER_ID, ProgressionModel.RPE_AUTOREGULATION);

        // Production code mutates the existing entity then saves the same reference.
        assertThat(existing.getProgressionModel()).isEqualTo(ProgressionModel.RPE_AUTOREGULATION);
        verify(preferenceRepository).save(existing);
    }

    @Test
    void updatePreference_noExistingPreference_createsAndSavesNew() {
        when(preferenceRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        service.updatePreference(USER_ID, ProgressionModel.LINEAR_PERIODIZATION);

        // Capture the new entity built inside the service to assert its state.
        ArgumentCaptor<UserProgressionPreferenceEntity> captor =
                ArgumentCaptor.forClass(UserProgressionPreferenceEntity.class);
        verify(preferenceRepository).save(captor.capture());

        UserProgressionPreferenceEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProgressionModel()).isEqualTo(ProgressionModel.LINEAR_PERIODIZATION);
    }
}