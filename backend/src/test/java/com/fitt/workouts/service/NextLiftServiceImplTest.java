package com.fitt.workouts.service;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.NextLiftResponse;
import com.fitt.workouts.dto.WorkoutPlanDayResponse;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Service unit test. Plan service and session repository are mocked so the day
// selection, the cycle math, and the today check are the only things under test.
@ExtendWith(MockitoExtension.class)
class NextLiftServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long PLAN_ID = 10L;

    @Mock
    private WorkoutPlanService workoutPlanService;

    @Mock
    private WorkoutSessionRepository sessionRepository;

    @InjectMocks
    private NextLiftServiceImpl service;

    @Test
    void getNextLift_noActivePlan_returnsEmptyState() {
        when(workoutPlanService.getActivePlan(USER_ID)).thenReturn(Optional.empty());

        NextLiftResponse response = service.getNextLift(USER_ID);

        assertThat(response.hasActivePlan()).isFalse();
        assertThat(response.day()).isNull();
        assertThat(response.planId()).isNull();
    }

    @Test
    void getNextLift_startOfRotation_returnsFirstDayWeekOne() {
        stubPlanAndTrainedToday(threeDayPlan(), false);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(0L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        // 0 completed: day index 0 = day 1, 0 rotations = week 1.
        assertThat(response.hasActivePlan()).isTrue();
        assertThat(response.dayNumber()).isEqualTo(1);
        assertThat(response.day().dayLabel()).isEqualTo("Push");
        assertThat(response.weekInCycle()).isEqualTo(1);
        assertThat(response.deload()).isFalse();
        assertThat(response.daysInRotation()).isEqualTo(3);
    }

    @Test
    void getNextLift_midRotation_selectsCorrectDay() {
        stubPlanAndTrainedToday(threeDayPlan(), false);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(2L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        // 2 completed: day index 2 = day 3 (Legs), still week 1.
        assertThat(response.dayNumber()).isEqualTo(3);
        assertThat(response.day().dayLabel()).isEqualTo("Legs");
        assertThat(response.weekInCycle()).isEqualTo(1);
    }

    @Test
    void getNextLift_oneRotationComplete_wrapsToFirstDayWeekTwo() {
        stubPlanAndTrainedToday(threeDayPlan(), false);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(3L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        // 3 / 3 = 1 rotation: day wraps back to 1, week 2.
        assertThat(response.dayNumber()).isEqualTo(1);
        assertThat(response.weekInCycle()).isEqualTo(2);
        assertThat(response.deload()).isFalse();
    }

    @Test
    void getNextLift_thirdRotation_isDeloadWeek() {
        stubPlanAndTrainedToday(threeDayPlan(), false);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(9L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        // 9 / 3 = 3 rotations = week 4 = deload.
        assertThat(response.weekInCycle()).isEqualTo(4);
        assertThat(response.deload()).isTrue();
    }

    @Test
    void getNextLift_manyCompletedSessions_doesNotFreeze() {
        stubPlanAndTrainedToday(threeDayPlan(), false);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(18L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        // 18 / 3 = 6 rotations, 6 % 4 = 2, week 3. Well past any session-list cap.
        assertThat(response.weekInCycle()).isEqualTo(3);
        assertThat(response.dayNumber()).isEqualTo(1);
    }

    @Test
    void getNextLift_sessionFinishedToday_flagsAlreadyTrained() {
        stubPlanAndTrainedToday(threeDayPlan(), true);
        when(sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(USER_ID, PLAN_ID))
                .thenReturn(0L);

        NextLiftResponse response = service.getNextLift(USER_ID);

        assertThat(response.alreadyTrainedToday()).isTrue();
    }

    @Test
    void getNextLift_planWithNoDays_returnsPlanWithoutDayAndNoDivision() {
        WorkoutPlanResponse emptyPlan = new WorkoutPlanResponse(
                PLAN_ID, USER_ID, "Empty", 0, SplitType.CUSTOM, true, List.of());
        stubPlanAndTrainedToday(emptyPlan, false);

        NextLiftResponse response = service.getNextLift(USER_ID);

        assertThat(response.hasActivePlan()).isTrue();
        assertThat(response.day()).isNull();
        assertThat(response.daysInRotation()).isEqualTo(0);
    }

    private void stubPlanAndTrainedToday(WorkoutPlanResponse plan, boolean trainedToday) {
        when(workoutPlanService.getActivePlan(USER_ID)).thenReturn(Optional.of(plan));
        when(sessionRepository.existsByOwnerUserIdAndFinishedAtGreaterThanEqualAndFinishedAtLessThan(
                eq(USER_ID), any(Instant.class), any(Instant.class))).thenReturn(trainedToday);
    }

    // A 3-day PPL plan. Days passed out of order on purpose, to prove the service
    // sorts by orderIndex before it selects the next day.
    private WorkoutPlanResponse threeDayPlan() {
        WorkoutPlanDayResponse push = new WorkoutPlanDayResponse(1L, 0, "Push", List.of());
        WorkoutPlanDayResponse pull = new WorkoutPlanDayResponse(2L, 1, "Pull", List.of());
        WorkoutPlanDayResponse legs = new WorkoutPlanDayResponse(3L, 2, "Legs", List.of());
        return new WorkoutPlanResponse(
                PLAN_ID, USER_ID, "PPL", 3, SplitType.PPL, true, List.of(legs, push, pull));
    }
}