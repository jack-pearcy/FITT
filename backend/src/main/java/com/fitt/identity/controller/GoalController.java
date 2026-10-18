package com.fitt.identity.controller;

import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Goals get their own route off the onboarding path, because a user changes
// a goal any time, not just during first setup. Same ownership guard every
// other controller uses: the path userId must match the one in the token.
@RestController
@RequestMapping("/api/users/{userId}/identity/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<Void> saveGoals(
            @PathVariable Long userId,
            @Valid @RequestBody SaveGoalsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        goalService.saveGoals(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<GoalsResponse> getGoals(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(goalService.getGoals(userId));
    }

    private void enforceOwnership(Long pathUserId, Long currentUserId) {
        if (!pathUserId.equals(currentUserId)) {
            throw new AccessDeniedException("access denied");
        }
    }

    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}