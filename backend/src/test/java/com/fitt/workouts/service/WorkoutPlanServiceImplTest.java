package com.fitt.workouts.service;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.*;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.WorkoutPlanDayEntity;
import com.fitt.workouts.persistence.entity.WorkoutPlanEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutPlanRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Service test for WorkoutPlanServiceImpl. Both repositories mocked. Covers the
// hierarchical createPlan build, the loadAndVerify ownership pattern shared by
// get and delete, and the activatePlan state-transition that deactivates all
// owned plans then activates the target.
@ExtendWith(MockitoExtension.class)
class WorkoutPlanServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long PLAN_ID = 200L;
    private static final long OTHER_PLAN_ID = 201L;
    private static final long EXERCISE_ID = 50L;

    @Mock
    private WorkoutPlanRepository planRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @InjectMocks
    private WorkoutPlanServiceImpl service;

    @Test
    void createPlan_withDayAndExercises_savesPlanWithCorrectHierarchy() {
        AddPlanDayExerciseRequest exReq = new AddPlanDayExerciseRequest(EXERCISE_ID, 3, 10);
        CreateWorkoutPlanDayRequest dayReq = new CreateWorkoutPlanDayRequest(
                1, "Push", List.of(exReq));
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "PPL 6-day", 6, SplitType.PPL, List.of(dayReq));

        when(exerciseRepository.findById(EXERCISE_ID))
                .thenReturn(Optional.of(sampleExercise("Bench Press")));
        when(planRepository.save(any(WorkoutPlanEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutPlanResponse response = service.createPlan(USER_ID, request);

        // Each exercise referenced in the request is looked up by id before being
        // attached to its plan day slot.
        verify(exerciseRepository).findById(EXERCISE_ID);

        // The plan saved to the repository carries the constructed hierarchy.
        ArgumentCaptor<WorkoutPlanEntity> captor =
                ArgumentCaptor.forClass(WorkoutPlanEntity.class);
        verify(planRepository).save(captor.capture());
        WorkoutPlanEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getName()).isEqualTo("PPL 6-day");
        assertThat(saved.getDaysPerWeek()).isEqualTo(6);
        assertThat(saved.getSplitType()).isEqualTo("PPL");
        assertThat(saved.getDays()).hasSize(1);

        // Response reflects what was built and mapped.
        assertThat(response.name()).isEqualTo("PPL 6-day");
        assertThat(response.splitType()).isEqualTo(SplitType.PPL);
    }

    @Test
    void createPlan_exerciseNotFound_throwsExerciseNotFoundException() {
        AddPlanDayExerciseRequest exReq = new AddPlanDayExerciseRequest(EXERCISE_ID, 3, 10);
        CreateWorkoutPlanDayRequest dayReq = new CreateWorkoutPlanDayRequest(
                1, "Push", List.of(exReq));
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "PPL 6-day", 6, SplitType.PPL, List.of(dayReq));

        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPlan(USER_ID, request))
                .isInstanceOf(ExerciseNotFoundException.class);

        verify(planRepository, never()).save(any(WorkoutPlanEntity.class));
    }

    @Test
    void updatePlan_replacesDaysAndScalars_preservesActiveFlag() {
        // existing plan already has a day and is active, owned by USER_ID
        WorkoutPlanEntity existing = samplePlan("Old name", true);
        existing.addDay(new WorkoutPlanDayEntity("Old day", 0));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(existing));

        // request rebuilds the plan with new scalars and a different day
        AddPlanDayExerciseRequest exReq = new AddPlanDayExerciseRequest(EXERCISE_ID, 4, 8);
        CreateWorkoutPlanDayRequest dayReq = new CreateWorkoutPlanDayRequest(0, "Pull", List.of(exReq));
        UpdateWorkoutPlanRequest request = new UpdateWorkoutPlanRequest(
                "New name", 4, SplitType.UPPER_LOWER, List.of(dayReq));

        when(exerciseRepository.findById(EXERCISE_ID))
                .thenReturn(Optional.of(sampleExercise("Row")));

        WorkoutPlanResponse response = service.updatePlan(USER_ID, PLAN_ID, request);

        // scalars applied to the managed entity
        assertThat(existing.getName()).isEqualTo("New name");
        assertThat(existing.getDaysPerWeek()).isEqualTo(4);
        assertThat(existing.getSplitType()).isEqualTo("UPPER_LOWER");

        // old day gone, exactly the new one present
        assertThat(existing.getDays()).hasSize(1);
        assertThat(existing.getDays().get(0).getDayLabel()).isEqualTo("Pull");
        assertThat(existing.getDays().get(0).getExercises()).hasSize(1);

        // an edit must not touch the active flag, activation is its own endpoint
        assertThat(existing.isActive()).isTrue();

        // response reflects the rebuilt plan
        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.days()).hasSize(1);
    }

    @Test
    void updatePlan_exerciseNotFound_throwsExerciseNotFoundException() {
        WorkoutPlanEntity existing = samplePlan("Old name", false);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(existing));

        AddPlanDayExerciseRequest exReq = new AddPlanDayExerciseRequest(EXERCISE_ID, 3, 10);
        CreateWorkoutPlanDayRequest dayReq = new CreateWorkoutPlanDayRequest(0, "Push", List.of(exReq));
        UpdateWorkoutPlanRequest request = new UpdateWorkoutPlanRequest(
                "New name", 4, SplitType.PPL, List.of(dayReq));

        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePlan(USER_ID, PLAN_ID, request))
                .isInstanceOf(ExerciseNotFoundException.class);
    }

    @Test
    void updatePlan_planNotFound_throwsWorkoutPlanNotFoundException() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        UpdateWorkoutPlanRequest request = new UpdateWorkoutPlanRequest(
                "New name", 4, SplitType.PPL, List.of());

        assertThatThrownBy(() -> service.updatePlan(USER_ID, PLAN_ID, request))
                .isInstanceOf(WorkoutPlanNotFoundException.class);
    }

    @Test
    void updatePlan_notOwned_throwsAccessDeniedException() {
        WorkoutPlanEntity foreign = new WorkoutPlanEntity(OTHER_USER_ID, "Their plan", 6, "PPL");
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(foreign));

        UpdateWorkoutPlanRequest request = new UpdateWorkoutPlanRequest(
                "New name", 4, SplitType.PPL, List.of());

        assertThatThrownBy(() -> service.updatePlan(USER_ID, PLAN_ID, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listPlans_returnsAllOwnedPlansMappedToResponse() {
        WorkoutPlanEntity p1 = samplePlan("PPL", true);
        WorkoutPlanEntity p2 = samplePlan("Upper Lower", false);
        when(planRepository.findByOwnerUserId(USER_ID)).thenReturn(List.of(p1, p2));

        List<WorkoutPlanResponse> result = service.listPlans(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WorkoutPlanResponse::name)
                .containsExactly("PPL", "Upper Lower");
        assertThat(result).extracting(WorkoutPlanResponse::active)
                .containsExactly(true, false);
    }

    @Test
    void getPlan_ownedPlan_returnsResponse() {
        WorkoutPlanEntity plan = samplePlan("PPL 6-day", true);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        WorkoutPlanResponse response = service.getPlan(USER_ID, PLAN_ID);

        assertThat(response.name()).isEqualTo("PPL 6-day");
    }

    @Test
    void getPlan_notFound_throwsWorkoutPlanNotFoundException() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(USER_ID, PLAN_ID))
                .isInstanceOf(WorkoutPlanNotFoundException.class);
    }

    @Test
    void getPlan_notOwned_throwsAccessDeniedException() {
        // Plan exists but owner is OTHER_USER_ID. Caller is USER_ID.
        // loadAndVerify guards against cross-user reads at the service layer.
        WorkoutPlanEntity foreignPlan = new WorkoutPlanEntity(
                OTHER_USER_ID, "Their plan", 6, "PPL");
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(foreignPlan));

        assertThatThrownBy(() -> service.getPlan(USER_ID, PLAN_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activatePlan_deactivatesAllOwnedPlansThenActivatesTarget() {
        WorkoutPlanEntity wasActive = samplePlan("Old plan", true);
        ReflectionTestUtils.setField(wasActive, "id", OTHER_PLAN_ID);
        WorkoutPlanEntity target = samplePlan("New plan", false);
        ReflectionTestUtils.setField(target, "id", PLAN_ID);

        when(planRepository.findByOwnerUserId(USER_ID))
                .thenReturn(List.of(wasActive, target));
        when(planRepository.save(any(WorkoutPlanEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutPlanResponse response = service.activatePlan(USER_ID, PLAN_ID);

        // Other plans deactivated, target now active. Entities mutated in place.
        assertThat(wasActive.isActive()).isFalse();
        assertThat(target.isActive()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void activatePlan_targetIdNotInUserPlans_throwsWorkoutPlanNotFoundException() {
        WorkoutPlanEntity p1 = samplePlan("Only plan", true);
        ReflectionTestUtils.setField(p1, "id", OTHER_PLAN_ID);
        when(planRepository.findByOwnerUserId(USER_ID)).thenReturn(List.of(p1));

        assertThatThrownBy(() -> service.activatePlan(USER_ID, PLAN_ID))
                .isInstanceOf(WorkoutPlanNotFoundException.class);
    }

    @Test
    void deletePlan_ownedPlan_callsRepositoryDelete() {
        WorkoutPlanEntity plan = samplePlan("PPL", true);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        service.deletePlan(USER_ID, PLAN_ID);

        verify(planRepository).delete(plan);
    }

    // Helper that builds a plan owned by USER_ID with the active flag set
    // via the public setter. Id is left null unless the test sets it via
    // reflection, because most tests do not care about it.
    private WorkoutPlanEntity samplePlan(String name, boolean active) {
        WorkoutPlanEntity plan = new WorkoutPlanEntity(USER_ID, name, 6, "PPL");
        plan.setActive(active);
        return plan;
    }

    private ExerciseEntity sampleExercise(String name) {
        return new ExerciseEntity(USER_ID, name, "CHEST", true);
    }
}