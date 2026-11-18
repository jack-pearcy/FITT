package com.fitt.workouts.service;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.CreateWorkoutPlanDayRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.UpdateWorkoutPlanRequest;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The plan rotation against a real database: sessions are created, finished and
// deleted through the services, and the next lift is read back each time.
// No test-level transaction on purpose, so each service call commits by itself.
@SpringBootTest
@ActiveProfiles("test")
class PlanRotationTest {

    @Autowired
    private WorkoutPlanService planService;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private NextLiftService nextLiftService;

    @Test
    void deletingAnOlderWorkout_doesNotMoveThePlanBack() {
        long userId = 401L;
        long planId = activePlan(userId, "Push", "Pull", "Legs");

        long first = trainNextDay(userId, planId);
        trainNextDay(userId, planId);
        assertThat(nextDay(userId)).isEqualTo("Legs");

        sessionService.deleteSession(userId, first);

        // Counting finished sessions would say Pull again. Pull was the last day trained.
        assertThat(nextDay(userId)).isEqualTo("Legs");
    }

    @Test
    void deletingTheLatestWorkout_offersItsDayAgain() {
        long userId = 402L;
        long planId = activePlan(userId, "Push", "Pull", "Legs");

        trainNextDay(userId, planId);
        long second = trainNextDay(userId, planId);
        sessionService.deleteSession(userId, second);

        assertThat(nextDay(userId)).isEqualTo("Pull");
    }

    @Test
    void addingADay_doesNotChangeWhichDayIsNext() {
        long userId = 403L;
        long planId = activePlan(userId, "Push", "Pull", "Legs");
        for (int i = 0; i < 4; i++) {
            trainNextDay(userId, planId);
        }
        // Push, Pull, Legs, Push are done.
        assertThat(nextDay(userId)).isEqualTo("Pull");

        planService.updatePlan(userId, planId, new UpdateWorkoutPlanRequest(
                "Plan", 4, SplitType.CUSTOM, days("Push", "Pull", "Legs", "Arms")));

        // Counting four finished sessions over four days would say Push.
        assertThat(nextDay(userId)).isEqualTo("Pull");
    }

    private long activePlan(long userId, String... labels) {
        WorkoutPlanResponse plan = planService.createPlan(
                userId, new CreateWorkoutPlanRequest("Plan", 3, SplitType.CUSTOM, days(labels)));
        planService.activatePlan(userId, plan.id());
        return plan.id();
    }

    private static List<CreateWorkoutPlanDayRequest> days(String... labels) {
        List<CreateWorkoutPlanDayRequest> days = new java.util.ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            days.add(new CreateWorkoutPlanDayRequest(i, labels[i], List.of()));
        }
        return days;
    }

    // Starts and finishes a session for the plan. Returns the session id.
    private long trainNextDay(long userId, long planId) {
        long id = sessionService.createSession(
                userId, new CreateWorkoutSessionRequest(Instant.now(), planId, null)).id();
        sessionService.finishSession(userId, id);
        return id;
    }

    private String nextDay(long userId) {
        return nextLiftService.getNextLift(userId).day().dayLabel();
    }
}
