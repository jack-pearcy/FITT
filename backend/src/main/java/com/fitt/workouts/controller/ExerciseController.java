package com.fitt.workouts.controller;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.service.ExerciseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Thin controller, HTTP concerns only.
// Enforces ownership then hands off to the service. No business logic lives here.
@RestController
@RequestMapping("/api/users/{userId}/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    // Returns global library + user's custom exercises.
    // muscleGroup filters the result when provided, returns everything if absent.
    @GetMapping
    public List<ExerciseResponse> listExercises(
            @PathVariable Long userId,
            @RequestParam(required = false) MuscleGroup muscleGroup,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return exerciseService.listExercises(userId, muscleGroup);
    }

    @PostMapping
    public ResponseEntity<ExerciseResponse> createCustomExercise(
            @PathVariable Long userId,
            @RequestBody CreateExerciseRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exerciseService.createCustomExercise(userId, request));
    }

    // 204 because there is nothing to return after a delete.
    @DeleteMapping("/{exerciseId}")
    public ResponseEntity<Void> deleteCustomExercise(
            @PathVariable Long userId,
            @PathVariable Long exerciseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        exerciseService.deleteCustomExercise(userId, exerciseId);
        return ResponseEntity.noContent().build();
    }

    // uid was encoded as Long but JSON has no Long/Integer distinction.
    // Nimbus may return Integer or Long depending on the value.
    // Casting to Number first covers both cases safely.
    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    // If the path userId and the JWT uid don't match this is a cross-user access attempt.
    // AccessDeniedException is caught by RestAccessDeniedHandler and returns 403.
    private void enforceOwnership(Long pathUserId, Long jwtUserId) {
        if (!pathUserId.equals(jwtUserId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
