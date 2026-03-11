package com.fitt.workouts.controller;

import com.fitt.workouts.dto.CreateWorkoutRequest;
import com.fitt.workouts.dto.WorkoutResponse;
import com.fitt.workouts.service.WorkoutService;
import com.fitt.workouts.service.model.WorkoutView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/workouts")
public class WorkoutsController {

    private final WorkoutService workoutService;

    public WorkoutsController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    // Ownership enforcement. Runs at the top of every endpoint before any service logic.
    //
    // Authentication is already done by the JWT filter before reaching here.
    // This is authorization, whether the caller owns this particular resource.
    //
    // The uid claim gets pulled from the verified JWT and compared to the {userId} path variable.
    // A mismatch means someone is trying to act on another user's data, so it returns 403 immediately.
    //
    // This is OWASP API Top 10 #1, Broken Object Level Authorization.
    // One of the most common vulnerabilities found in production APIs.
    private void enforceOwnership(Jwt jwt, Long userId) {
        Long principalId = jwt.getClaim("uid");
        if (principalId == null || !principalId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: resource does not belong to the authenticated user");
        }
    }

    // @AuthenticationPrincipal Jwt tells Spring to inject the already-verified JWT object.
    // Token validation happened in the filter before this method was ever called.
    // It is used here only to extract claims for the ownership check.
    @PostMapping
    public ResponseEntity<WorkoutResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestBody CreateWorkoutRequest request
    ) {
        enforceOwnership(jwt, userId);

        WorkoutView created = workoutService.createWorkout(userId, request.getName());

        WorkoutResponse body = new WorkoutResponse(
                created.id(),
                created.name(),
                created.createdAt(),
                created.updatedAt()
        );

        // 201 Created with a Location header pointing to the new resource. Correct REST semantics.
        URI location = URI.create("/api/users/" + userId + "/workouts/" + created.id());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    public ResponseEntity<List<WorkoutResponse>> getWorkouts(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        enforceOwnership(jwt, userId);

        List<WorkoutResponse> workouts = workoutService.getWorkouts(userId)
                .stream()
                .map(w -> new WorkoutResponse(w.id(), w.name(), w.createdAt(), w.updatedAt()))
                .toList();

        return ResponseEntity.ok(workouts);
    }

    @GetMapping("/{workoutId}")
    public WorkoutResponse getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @PathVariable Long workoutId
    ) {
        enforceOwnership(jwt, userId);

        WorkoutView workout = workoutService.getWorkoutById(userId, workoutId);

        return new WorkoutResponse(
                workout.id(),
                workout.name(),
                workout.createdAt(),
                workout.updatedAt()
        );
    }

    @DeleteMapping("/{workoutId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @PathVariable Long workoutId
    ) {
        enforceOwnership(jwt, userId);
        workoutService.deleteWorkout(userId, workoutId);
        // 204 No Content. Worked, nothing to send back.
        return ResponseEntity.noContent().build();
    }
}