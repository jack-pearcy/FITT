package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.AddPlanDayExerciseRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanDayRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.PlanDayExerciseResponse;
import com.fitt.workouts.dto.WorkoutPlanDayResponse;
import com.fitt.workouts.dto.WorkoutPlanResponse;
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

        for (CreateWorkoutPlanDayRequest dayReq : request.days()) {
            // entity constructor: dayLabel, orderIndex
            WorkoutPlanDayEntity day = new WorkoutPlanDayEntity(dayReq.dayLabel(), dayReq.orderIndex());

            if (dayReq.exercises() != null) {
                int order = 0;
                for (AddPlanDayExerciseRequest exReq : dayReq.exercises()) {
                    ExerciseEntity exercise = exerciseRepository.findById(exReq.exerciseId())
                            .orElseThrow(() -> new ExerciseNotFoundException(exReq.exerciseId()));

                    // entity constructor: exercise, sets, reps, orderIndex
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

        return toResponse(planRepository.save(plan));
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