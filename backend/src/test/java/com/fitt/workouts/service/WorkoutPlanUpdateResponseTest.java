package com.fitt.workouts.service;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.AddPlanDayExerciseRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanDayRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.UpdateWorkoutPlanRequest;
import com.fitt.workouts.dto.WorkoutPlanDayResponse;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Updating a plan against a real database and a real transaction. The point of
// this class is the one thing a mocked repository cannot show: the rebuilt days
// and exercises only get their ids when they are inserted, so the response has
// to be built after that.
// No test-level transaction on purpose, so each service call commits by itself.
@SpringBootTest
@ActiveProfiles("test")
class WorkoutPlanUpdateResponseTest {

    private static final long USER_ID = 201L;

    @Autowired
    private WorkoutPlanService service;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Test
    void updatePlan_responseCarriesTheIdsOfTheRebuiltDaysAndExercises() {
        ExerciseEntity squat = exerciseRepository.save(new ExerciseEntity(null, "Back Squat", "LEGS", false));
        List<CreateWorkoutPlanDayRequest> days = List.of(
                new CreateWorkoutPlanDayRequest(0, "Legs", List.of(new AddPlanDayExerciseRequest(squat.getId(), 3, 10))),
                new CreateWorkoutPlanDayRequest(1, "Rest", List.of())
        );
        WorkoutPlanResponse created = service.createPlan(
                USER_ID, new CreateWorkoutPlanRequest("Original", 3, SplitType.CUSTOM, days));

        WorkoutPlanResponse updated = service.updatePlan(
                USER_ID, created.id(), new UpdateWorkoutPlanRequest("Renamed", 4, SplitType.CUSTOM, days));

        assertThat(updated.days()).extracting(WorkoutPlanDayResponse::id).doesNotContainNull();
        assertThat(updated.days().getFirst().exercises()).hasSize(1)
                .allSatisfy(slot -> assertThat(slot.id()).isNotNull());

        // The response is the plan as stored: the same ids a read returns afterwards.
        assertThat(updated).isEqualTo(service.getPlan(USER_ID, created.id()));
    }
}
