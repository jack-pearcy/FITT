package com.fitt.workouts.service;

import com.fitt.workouts.dto.NextLiftResponse;
import com.fitt.workouts.dto.WorkoutPlanDayResponse;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class NextLiftServiceImpl implements NextLiftService {

    // Linear periodization cycle length in weeks. Mirrors LinearPeriodizationStrategy.
    // The deload lands on the last week of the cycle.
    private static final int CYCLE_LENGTH = 4;

    private final WorkoutPlanService workoutPlanService;
    private final WorkoutSessionRepository sessionRepository;

    public NextLiftServiceImpl(WorkoutPlanService workoutPlanService,
                               WorkoutSessionRepository sessionRepository) {
        this.workoutPlanService = workoutPlanService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public NextLiftResponse getNextLift(Long ownerUserId) {
        Optional<WorkoutPlanResponse> active = workoutPlanService.getActivePlan(ownerUserId);
        if (active.isEmpty()) {
            return new NextLiftResponse(false, null, null, 0, 0, 1, false, false, null);
        }

        WorkoutPlanResponse plan = active.get();

        // Days are not guaranteed to come back ordered, so sort by orderIndex, the
        // same ordering the rotation cursor relies on.
        List<WorkoutPlanDayResponse> days = plan.days().stream()
                .sorted(Comparator.comparingInt(WorkoutPlanDayResponse::orderIndex))
                .toList();

        boolean trainedToday = alreadyTrainedToday(ownerUserId);

        // A plan with no days has nothing to train. Return the plan without a day
        // and guard the division below against a zero rotation length.
        if (days.isEmpty()) {
            return new NextLiftResponse(true, plan.id(), plan.name(), 0, 0, 1, false,
                    trainedToday, null);
        }

        int daysInRotation = days.size();
        long completedSessions =
                sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(ownerUserId, plan.id());

        // Which day is next = completed sessions wrapped by the rotation length.
        int dayIndex = (int) (completedSessions % daysInRotation);
        WorkoutPlanDayResponse nextDay = days.get(dayIndex);

        // Where in the cycle = one full rotation is one week, wrapped by the cycle length.
        long rotationsCompleted = completedSessions / daysInRotation;
        int weekInCycle = (int) (rotationsCompleted % CYCLE_LENGTH) + 1;
        boolean deload = weekInCycle == CYCLE_LENGTH;

        return new NextLiftResponse(
                true,
                plan.id(),
                plan.name(),
                daysInRotation,
                dayIndex + 1,
                weekInCycle,
                deload,
                trainedToday,
                nextDay
        );
    }

    // A session counts as today when it finished between the start of today and the
    // start of tomorrow in the server zone. finishedAt is null for in progress
    // sessions, which the range excludes.
    private boolean alreadyTrainedToday(Long ownerUserId) {
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        return sessionRepository.existsByOwnerUserIdAndFinishedAtGreaterThanEqualAndFinishedAtLessThan(
                ownerUserId, startOfToday, startOfTomorrow);
    }
}