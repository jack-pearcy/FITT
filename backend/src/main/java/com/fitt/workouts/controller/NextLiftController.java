package com.fitt.workouts.controller;

import com.fitt.workouts.dto.NextLiftResponse;
import com.fitt.workouts.service.NextLiftService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Read endpoint for the Home and Today's Lift screen.
// Ownership enforced before any work, same pattern as the other controllers.
@RestController
@RequestMapping("/api/users/{userId}/next-lift")
public class NextLiftController {

    private final NextLiftService nextLiftService;

    public NextLiftController(NextLiftService nextLiftService) {
        this.nextLiftService = nextLiftService;
    }

    @GetMapping
    public NextLiftResponse getNextLift(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        enforceOwnership(userId, currentUserId(jwt));
        return nextLiftService.getNextLift(userId);
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