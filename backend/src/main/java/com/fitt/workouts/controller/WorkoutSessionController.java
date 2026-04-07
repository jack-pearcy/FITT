package com.fitt.workouts.controller;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.service.WorkoutSessionService;
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
@RequestMapping("/api/users/{userId}/workout-sessions")
public class WorkoutSessionController {

    private final WorkoutSessionService workoutSessionService;

    public WorkoutSessionController(WorkoutSessionService workoutSessionService) {
        this.workoutSessionService = workoutSessionService;
    }

    // 201 because a new session resource was created.
    @PostMapping
    public ResponseEntity<WorkoutSessionResponse> createSession(
            @PathVariable Long userId,
            @RequestBody CreateWorkoutSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workoutSessionService.createSession(userId, request));
    }

    @GetMapping
    public List<WorkoutSessionResponse> listSessions(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.listSessions(userId);
    }

    // Returns the number of completed sessions for a given plan.
    // The frontend uses this to determine which plan day comes next.
    @GetMapping("/count")
    public ResponseEntity<Long> countCompletedSessions(
            @PathVariable Long userId,
            @RequestParam Long planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(workoutSessionService.countCompletedSessions(userId, planId));
    }

    @GetMapping("/{sessionId}")
    public WorkoutSessionResponse getSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.getSession(userId, sessionId);
    }

    // Returns the updated session so the client can show finishedAt immediately.
    @PostMapping("/{sessionId}/finish")
    public WorkoutSessionResponse finishSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.finishSession(userId, sessionId);
    }

    // Top-level session delete. 204 because the resource is gone.
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        workoutSessionService.deleteSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    // Returns the updated session so the client sees the new entry list without a second request.
    @PostMapping("/{sessionId}/entries")
    public WorkoutSessionResponse addExerciseEntry(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @RequestBody AddExerciseEntryRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.addExerciseEntry(userId, sessionId, request);
    }

    // Returns the updated session (not 204) so the UI can re-render the entry list.
    @DeleteMapping("/{sessionId}/entries/{entryId}")
    public WorkoutSessionResponse removeExerciseEntry(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long entryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.removeExerciseEntry(userId, sessionId, entryId);
    }

    // Returns the updated session so set numbers and the full state are visible immediately.
    @PostMapping("/{sessionId}/entries/{entryId}/sets")
    public WorkoutSessionResponse logSet(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long entryId,
            @RequestBody LogSetRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.logSet(userId, sessionId, entryId, request);
    }

    // Returns the updated session (not 204) so the UI knows the new set list.
    @DeleteMapping("/{sessionId}/entries/{entryId}/sets/{setId}")
    public WorkoutSessionResponse deleteSet(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long entryId,
            @PathVariable Long setId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.deleteSet(userId, sessionId, entryId, setId);
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