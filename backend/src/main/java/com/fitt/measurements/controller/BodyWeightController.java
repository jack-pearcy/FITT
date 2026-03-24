package com.fitt.measurements.controller;

import com.fitt.measurements.dto.BodyWeightAverageResponse;
import com.fitt.measurements.dto.BodyWeightEntryResponse;
import com.fitt.measurements.dto.LogBodyWeightRequest;
import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;
import com.fitt.measurements.service.BodyWeightService;
import com.fitt.measurements.service.WeightLogResult;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/users/{userId}/measurements/weight")
public class BodyWeightController {

    private final BodyWeightService bodyWeightService;

    public BodyWeightController(BodyWeightService bodyWeightService) {
        this.bodyWeightService = bodyWeightService;
    }

    @PostMapping
    public ResponseEntity<BodyWeightEntryResponse> logWeight(
            @PathVariable Long userId,
            @Valid @RequestBody LogBodyWeightRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        enforceOwnership(userId, currentUserId(jwt));

        WeightLogResult result = bodyWeightService.logWeight(
                userId, request.loggedAt(), request.weight());

        BodyWeightEntryResponse response = toResponse(result.entry());

        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BodyWeightEntryResponse>> getEntries(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal Jwt jwt) {

        enforceOwnership(userId, currentUserId(jwt));

        List<BodyWeightEntryResponse> entries = bodyWeightService.getEntries(userId, days)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(entries);
    }

    @GetMapping("/average")
    public ResponseEntity<BodyWeightAverageResponse> getSevenDayAverage(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        enforceOwnership(userId, currentUserId(jwt));

        return ResponseEntity.ok(
                new BodyWeightAverageResponse(bodyWeightService.getSevenDayAverage(userId)));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable Long userId,
            @PathVariable Long entryId,
            @AuthenticationPrincipal Jwt jwt) {

        enforceOwnership(userId, currentUserId(jwt));

        bodyWeightService.deleteEntry(userId, entryId);
        return ResponseEntity.noContent().build();
    }

    private BodyWeightEntryResponse toResponse(BodyWeightEntryEntity entity) {
        return new BodyWeightEntryResponse(
                entity.getId(),
                entity.getLoggedAt(),
                entity.getWeight(),
                entity.getCreatedAt());
    }

    private void enforceOwnership(Long pathUserId, Long currentUserId) {
        if (!pathUserId.equals(currentUserId)) {
            throw new AccessDeniedException("User does not have access to this resource");
        }
    }

    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}