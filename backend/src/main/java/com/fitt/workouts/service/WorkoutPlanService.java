package com.fitt.workouts.service;

import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.UpdateWorkoutPlanRequest;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import java.util.List;
import java.util.Optional;

public interface WorkoutPlanService {

    Optional<WorkoutPlanResponse> getActivePlan(Long ownerUserId);

    WorkoutPlanResponse createPlan(Long ownerUserId, CreateWorkoutPlanRequest request);

    WorkoutPlanResponse updatePlan(Long ownerUserId, Long planId, UpdateWorkoutPlanRequest request);

    List<WorkoutPlanResponse> listPlans(Long ownerUserId);

    WorkoutPlanResponse getPlan(Long ownerUserId, Long planId);

    WorkoutPlanResponse activatePlan(Long ownerUserId, Long planId);

    void deletePlan(Long ownerUserId, Long planId);
}