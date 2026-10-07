package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.*;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.PlanDayExerciseEntity;
import com.fitt.workouts.persistence.entity.WorkoutPlanDayEntity;
import com.fitt.workouts.persistence.entity.WorkoutPlanEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutPlanRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkoutPlanServiceImpl implements WorkoutPlanService {

    private final WorkoutPlanRepository planRepository;
    private final ExerciseRepository exerciseRepository;

    public WorkoutPlanServiceImpl(WorkoutPlanRepository planRepository,
                                  ExerciseRepository exerciseRepository) {
        this.planRepository = planRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    @Transactional
    public WorkoutPlanResponse createPlan(Long ownerUserId, CreateWorkoutPlanRequest request) {
        // entity constructor: ownerUserId, name, daysPerWeek, splitType
        WorkoutPlanEntity plan = new WorkoutPlanEntity(
                ownerUserId,
                request.name(),
                request.daysPerWeek(),
                request.splitType().name()
        );

        addDaysFromRequest(plan, request.days());

        return toResponse(planRepository.save(plan));
    }

    @Override
    public Optional<WorkoutPlanResponse> getActivePlan(Long ownerUserId) {
        return planRepository.findByOwnerUserIdAndIsActiveTrue(ownerUserId)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public WorkoutPlanResponse updatePlan(Long ownerUserId, Long planId, UpdateWorkoutPlanRequest request) {
        // load the existing plan, which also runs the ownership check and 404
        WorkoutPlanEntity plan = loadAndVerify(ownerUserId, planId);

        // scalars go straight onto the managed entity
        plan.setName(request.name());
        plan.setDaysPerWeek(request.daysPerWeek());
        plan.setSplitType(request.splitType().name());

        // full replace: drop the old days before rebuilding from the request
        plan.clearDays();

        addDaysFromRequest(plan, request.days());

        // plan is managed so dirty checking flushes the changes at commit. no save needed.
        return toResponse(plan);
    }

    @Override
    public List<WorkoutPlanResponse> listPlans(Long ownerUserId) {
        return planRepository.findByOwnerUserId(ownerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public WorkoutPlanResponse getPlan(Long ownerUserId, Long planId) {
        return toResponse(loadAndVerify(ownerUserId, planId));
    }

    @Override
    @Transactional
    public WorkoutPlanResponse activatePlan(Long ownerUserId, Long planId) {
        List<WorkoutPlanEntity> allPlans = planRepository.findByOwnerUserId(ownerUserId);

        // deactivate all first then activate the target
        allPlans.forEach(p -> p.setActive(false));
        planRepository.saveAll(allPlans);

        WorkoutPlanEntity target = allPlans.stream()
                .filter(p -> p.getId().equals(planId))
                .findFirst()
                .orElseThrow(() -> new WorkoutPlanNotFoundException(planId));

        target.setActive(true);

        return toResponse(planRepository.save(target));
    }

    @Override
    @Transactional
    public void deletePlan(Long ownerUserId, Long planId) {
        planRepository.delete(loadAndVerify(ownerUserId, planId));
    }

    private WorkoutPlanEntity loadAndVerify(Long ownerUserId, Long planId) {
        WorkoutPlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new WorkoutPlanNotFoundException(planId));

        if (!ownerUserId.equals(plan.getOwnerUserId())) {
            throw new AccessDeniedException("You do not own this plan");
        }

        return plan;
    }

    // shared by createPlan and updatePlan so both build the day graph the same way.
    // works on the passed in plan and syncs both sides through addDay/addExercise.
    private void addDaysFromRequest(WorkoutPlanEntity plan, List<CreateWorkoutPlanDayRequest> dayReqs) {
        for (CreateWorkoutPlanDayRequest dayReq : dayReqs) {
            // entity constructor: dayLabel, orderIndex
            WorkoutPlanDayEntity day = new WorkoutPlanDayEntity(dayReq.dayLabel(), dayReq.orderIndex());

            if (dayReq.exercises() != null) {
                int order = 0;
                for (AddPlanDayExerciseRequest exReq : dayReq.exercises()) {
                    ExerciseEntity exercise = exerciseRepository
                            .findAccessibleById(exReq.exerciseId(), plan.getOwnerUserId())
                            .orElseThrow(() -> new ExerciseNotFoundException(exReq.exerciseId()));

                    // entity constructor, exercise, sets, reps, orderIndex
                    PlanDayExerciseEntity slot = new PlanDayExerciseEntity(
                            exercise,
                            exReq.sets(),
                            exReq.reps(),
                            order++
                    );

                    // addExercise syncs both sides
                    day.addExercise(slot);
                }
            }

            // addDay syncs both sides
            plan.addDay(day);
        }
    }

    private WorkoutPlanResponse toResponse(WorkoutPlanEntity entity) {
        List<WorkoutPlanDayResponse> days = entity.getDays().stream()
                .map(this::toDayResponse)
                .toList();

        return new WorkoutPlanResponse(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getDaysPerWeek(),
                SplitType.valueOf(entity.getSplitType()),
                entity.isActive(),
                days
        );
    }

    private WorkoutPlanDayResponse toDayResponse(WorkoutPlanDayEntity entity) {
        List<PlanDayExerciseResponse> exercises = entity.getExercises().stream()
                .map(this::toSlotResponse)
                .toList();

        return new WorkoutPlanDayResponse(
                entity.getId(),
                entity.getOrderIndex(),
                entity.getDayLabel(),
                exercises
        );
    }

    private PlanDayExerciseResponse toSlotResponse(PlanDayExerciseEntity entity) {
        ExerciseResponse exercise = new ExerciseResponse(
                entity.getExercise().getId(),
                entity.getExercise().getName(),
                MuscleGroup.valueOf(entity.getExercise().getMuscleGroup()),
                entity.getExercise().isCustom()
        );

        return new PlanDayExerciseResponse(
                entity.getId(),
                entity.getOrderIndex(),
                entity.getSets(),
                entity.getReps(),
                exercise
        );
    }
}