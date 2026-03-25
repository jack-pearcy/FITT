package com.fitt.workouts.controller;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;
import com.fitt.workouts.dto.UpdateProgressionPreferenceRequest;
import com.fitt.workouts.service.ProgressionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Thin controller for progression suggestions and preference management.
// Ownership enforced on every method before anything else runs.
@RestController
@RequestMapping("/api/users/{userId}/progression")
public class ProgressionController {

    private final ProgressionService progressionService;

    public ProgressionController(ProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ProgressionSuggestionResponse> getSuggestion(
            @PathVariable Long userId,
            @RequestParam Long exerciseId,
            @AuthenticationPrincipal Jwt jwt) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(progressionService.getSuggestion(userId, exerciseId));
    }

    @GetMapping("/preference")
    public ResponseEntity<ProgressionModel> getPreference(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(progressionService.getPreference(userId));
    }

    @PutMapping("/preference")
    public ResponseEntity<Void> updatePreference(
            @PathVariable Long userId,
            @RequestBody @Valid UpdateProgressionPreferenceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        enforceOwnership(userId, currentUserId(jwt));
        progressionService.updatePreference(userId, request.progressionModel());
        return ResponseEntity.noContent().build();
    }

    // uid was encoded as Long but JSON has no Long/Integer distinction.
    // Nimbus may return Integer or Long depending on the value.
    // Casting to Number first covers both cases safely.
    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    // If the path userId and the JWT uid don't match this is a cross-user access attempt.
    private void enforceOwnership(Long pathUserId, Long jwtUserId) {
        if (!pathUserId.equals(jwtUserId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}