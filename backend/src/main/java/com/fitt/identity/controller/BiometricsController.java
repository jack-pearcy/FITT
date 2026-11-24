package com.fitt.identity.controller;

import com.fitt.identity.dto.BiometricsResponse;
import com.fitt.identity.dto.UpdateUnitsRequest;
import com.fitt.identity.service.BiometricsService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/identity/biometrics")
public class BiometricsController {

    private final BiometricsService biometricsService;

    public BiometricsController(BiometricsService biometricsService) {
        this.biometricsService = biometricsService;
    }

    @GetMapping
    public ResponseEntity<BiometricsResponse> getBiometrics(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        return ResponseEntity.ok(biometricsService.getBiometrics(userId));
    }

    @PutMapping("/units")
    public ResponseEntity<Void> changeUnits(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUnitsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        enforceOwnership(userId, currentUserId(jwt));
        biometricsService.changeUnits(userId, request.unitsPreference());
        return ResponseEntity.noContent().build();
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
