package com.fitt.workouts.controller;

import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.UpdateWorkoutPlanRequest;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Thin controller, HTTP concerns only.
// Ownership is enforced on every method before anything else runs.
@RestController
@RequestMapping("/api/users/{userId}/workout-plans")
public class WorkoutPlanController {

    private final WorkoutPlanService workoutPlanService;

    public WorkoutPlanController(WorkoutPlanService workoutPlanService) {
        this.workoutPlanService = workoutPlanService;
    }

    // 201 because a new plan resource was created.
    @PostMapping
    public ResponseEntity<WorkoutPlanResponse> createPlan(
            @PathVariable Long userId,
            @RequestBody CreateWorkoutPlanRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workoutPlanService.createPlan(userId, request));
    }

    @GetMapping
    public List<WorkoutPlanResponse> listPlans(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutPlanService.listPlans(userId);
    }

    @GetMapping("/{planId}")
    public WorkoutPlanResponse getPlan(
            @PathVariable Long userId,
            @PathVariable Long planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutPlanService.getPlan(userId, planId);
    }

    // POST to an action sub-resource rather than PATCH on the plan directly.
    // Activation is a state transition, not a field edit.
    @PostMapping("/{planId}/activate")
    public WorkoutPlanResponse activatePlan(
            @PathVariable Long userId,
            @PathVariable Long planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutPlanService.activatePlan(userId, planId);
    }

    // PUT replaces the whole plan. 200 with the updated resource since the plan already exists.
    @PutMapping("/{planId}")
    public WorkoutPlanResponse updatePlan(
            @PathVariable Long userId,
            @PathVariable Long planId,
            @Valid @RequestBody UpdateWorkoutPlanRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutPlanService.updatePlan(userId, planId, request);
    }

    // 204 because the plan is gone and there is nothing to return.
    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(
            @PathVariable Long userId,
            @PathVariable Long planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        workoutPlanService.deletePlan(userId, planId);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private void enforceOwnership(Long pathUserId, Long jwtUserId) {
        if (!pathUserId.equals(jwtUserId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
