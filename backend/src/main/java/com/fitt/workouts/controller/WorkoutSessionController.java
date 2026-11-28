package com.fitt.workouts.controller;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.FinishSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.UpdateSessionNotesRequest;
import com.fitt.workouts.dto.UpdateSessionRequest;
import com.fitt.workouts.dto.UpdateSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.service.WorkoutSessionService;
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
@RequestMapping("/api/users/{userId}/workout-sessions")
public class WorkoutSessionController {

    private final WorkoutSessionService workoutSessionService;

    public WorkoutSessionController(WorkoutSessionService workoutSessionService) {
        this.workoutSessionService = workoutSessionService;
    }

    // 201 because a new session resource was created. A repeat with the same
    // client id answers 201 with the first session too, so a retry reads the same
    // as the request it repeats.
    @PostMapping
    public ResponseEntity<WorkoutSessionResponse> createSession(
            @PathVariable Long userId,
            @Valid @RequestBody CreateWorkoutSessionRequest request,
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

    // The session started and not yet finished, so a client can resume it, even
    // one started on another device. 204 when there is none.
    @GetMapping("/open")
    public ResponseEntity<WorkoutSessionResponse> getOpenSession(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.getOpenSession(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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
    // The body is optional: older apps send none and the web sends {}.
    @PostMapping("/{sessionId}/finish")
    public WorkoutSessionResponse finishSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) FinishSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.finishSession(userId, sessionId, request);
    }

    // Replaces the notes of a session, finished or not. Returns the updated
    // session so the client shows what was stored.
    @PutMapping("/{sessionId}/notes")
    public WorkoutSessionResponse updateNotes(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionNotesRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.updateNotes(userId, sessionId, request);
    }

    // Changes the start, the finish or the notes of a session, each optional.
    // Returns the updated session.
    @PatchMapping("/{sessionId}")
    public WorkoutSessionResponse updateSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.updateSession(userId, sessionId, request);
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
            @Valid @RequestBody AddExerciseEntryRequest request,
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
            @Valid @RequestBody LogSetRequest request,
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

    // Replaces the reps, weight and RPE of a logged set. Returns the updated session.
    @PutMapping("/{sessionId}/entries/{entryId}/sets/{setId}")
    public WorkoutSessionResponse updateSet(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long entryId,
            @PathVariable Long setId,
            @Valid @RequestBody UpdateSetRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return workoutSessionService.updateSet(userId, sessionId, entryId, setId, request);
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