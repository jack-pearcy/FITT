package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.repository.ExerciseEntryRepository;
import com.fitt.workouts.repository.PlanDayExerciseRepository;
import com.fitt.workouts.service.exception.ExerciseInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Service test for ExerciseServiceImpl. ExerciseRepository is mocked because
// it reaches JPA. Service logic stays under test: global vs custom filtering,
// muscle group filter, and the three guard conditions on delete (existence,
// custom-only, ownership).
@ExtendWith(MockitoExtension.class)
class ExerciseServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long EXERCISE_ID = 100L;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private PlanDayExerciseRepository planDayExerciseRepository;

    @Mock
    private ExerciseEntryRepository exerciseEntryRepository;

    @InjectMocks
    private ExerciseServiceImpl service;

    @Test
    void listExercises_noMuscleGroupFilter_returnsAllFromRepository() {
        ExerciseEntity globalChest = entity(null, "Bench Press", "CHEST", false);
        ExerciseEntity customLegs = entity(USER_ID, "My Squat Variation", "LEGS", true);
        when(exerciseRepository.findByOwnerUserIdIsNullOrOwnerUserId(USER_ID))
                .thenReturn(List.of(globalChest, customLegs));

        List<ExerciseResponse> result = service.listExercises(USER_ID, null);

        // Null filter short-circuits the predicate so every repo result passes.
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExerciseResponse::name)
                .containsExactly("Bench Press", "My Squat Variation");
    }

    @Test
    void listExercises_withMuscleGroupFilter_returnsOnlyMatching() {
        ExerciseEntity globalChest = entity(null, "Bench Press", "CHEST", false);
        ExerciseEntity globalLegs = entity(null, "Back Squat", "LEGS", false);
        ExerciseEntity customChest = entity(USER_ID, "Incline DB Press", "CHEST", true);
        when(exerciseRepository.findByOwnerUserIdIsNullOrOwnerUserId(USER_ID))
                .thenReturn(List.of(globalChest, globalLegs, customChest));

        List<ExerciseResponse> result = service.listExercises(USER_ID, MuscleGroup.CHEST);

        // Filter happens in memory after the repository returns. Only CHEST stays.
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExerciseResponse::name)
                .containsExactly("Bench Press", "Incline DB Press");
    }

    @Test
    void createCustomExercise_savesEntityWithCustomTrueAndOwnerUserId() {
        CreateExerciseRequest request = new CreateExerciseRequest(
                "Cable Row", MuscleGroup.BACK);
        ExerciseEntity saved = entity(USER_ID, "Cable Row", "BACK", true);
        when(exerciseRepository.save(any(ExerciseEntity.class))).thenReturn(saved);

        ExerciseResponse response = service.createCustomExercise(USER_ID, request);

        ArgumentCaptor<ExerciseEntity> captor = ArgumentCaptor.forClass(ExerciseEntity.class);
        verify(exerciseRepository).save(captor.capture());
        ExerciseEntity passed = captor.getValue();
        assertThat(passed.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(passed.getName()).isEqualTo("Cable Row");
        assertThat(passed.getMuscleGroup()).isEqualTo("BACK");
        assertThat(passed.isCustom()).isTrue();

        assertThat(response.name()).isEqualTo("Cable Row");
        assertThat(response.custom()).isTrue();
    }

    @Test
    void deleteCustomExercise_ownedCustomExercise_callsRepositoryDelete() {
        ExerciseEntity exercise = entity(USER_ID, "Incline DB Press", "CHEST", true);
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise));

        service.deleteCustomExercise(USER_ID, EXERCISE_ID);

        verify(exerciseRepository).delete(exercise);
    }

    @Test
    void deleteCustomExercise_notFound_throwsExerciseNotFoundException() {
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCustomExercise(USER_ID, EXERCISE_ID))
                .isInstanceOf(ExerciseNotFoundException.class);

        verify(exerciseRepository, never()).delete(any(ExerciseEntity.class));
    }

    @Test
    void deleteCustomExercise_globalLibraryExercise_throwsIllegalArgumentException() {
        // Global library entries have custom=false. They are seeded by the system
        // and must never be deletable by users.
        ExerciseEntity globalExercise = entity(null, "Bench Press", "CHEST", false);
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(globalExercise));

        assertThatThrownBy(() -> service.deleteCustomExercise(USER_ID, EXERCISE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fixed library");

        verify(exerciseRepository, never()).delete(any(ExerciseEntity.class));
    }

    @Test
    void deleteCustomExercise_customExerciseOwnedByDifferentUser_throwsAccessDeniedException() {
        // Custom exercise owned by OTHER_USER_ID. Caller is USER_ID. Should fail
        // even though the exercise is custom, because the ownership guard fires.
        ExerciseEntity othersCustomExercise = entity(OTHER_USER_ID, "Their Curl", "ARMS", true);
        when(exerciseRepository.findById(EXERCISE_ID))
                .thenReturn(Optional.of(othersCustomExercise));

        assertThatThrownBy(() -> service.deleteCustomExercise(USER_ID, EXERCISE_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(exerciseRepository, never()).delete(any(ExerciseEntity.class));
    }

    @Test
    void deleteCustomExercise_referencedByPlanOrSession_throwsExerciseInUseException() {
        ExerciseEntity exercise = entity(USER_ID, "My Row", "BACK", true);
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
        when(planDayExerciseRepository.existsByExercise_Id(EXERCISE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCustomExercise(USER_ID, EXERCISE_ID))
                .isInstanceOf(ExerciseInUseException.class);

        verify(exerciseRepository, never()).delete(any(ExerciseEntity.class));
    }

    // Helper that builds an ExerciseEntity without going through JPA. Id stays
    // null because the tests do not assert on it.
    private ExerciseEntity entity(Long ownerUserId, String name,
                                  String muscleGroup, boolean custom) {
        return new ExerciseEntity(ownerUserId, name, muscleGroup, custom);
    }
}