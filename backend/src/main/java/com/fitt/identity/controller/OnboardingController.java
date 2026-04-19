package com.fitt.identity.controller;

import com.fitt.identity.dto.*;
import com.fitt.identity.service.OnboardingService;
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

@RestController
@RequestMapping("/api/users/{userId}/identity/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/biometrics")
    public ResponseEntity<Void> saveBiometrics(
            @PathVariable Long userId,
            @Valid @RequestBody SaveBiometricsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        onboardingService.saveBiometrics(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/goals")
    public ResponseEntity<Void> saveGoals(
            @PathVariable Long userId,
            @Valid @RequestBody SaveGoalsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        onboardingService.saveGoals(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusResponse> getStatus(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    @GetMapping("/tdee")
    public ResponseEntity<TdeeResponse> getTdee(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(onboardingService.getTdee(userId));
    }

    @GetMapping("/goals")
    public ResponseEntity<GoalsResponse> getGoals(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(onboardingService.getGoals(userId));
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