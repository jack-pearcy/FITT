package com.fitt.coach.controller;

import com.fitt.coach.dto.CoachReviewResponse;
import com.fitt.coach.service.CoachService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The AI coach. Same ownership guard every other controller uses: the path
// userId must match the one in the token.
@RestController
@RequestMapping("/api/users/{userId}/coach")
public class CoachController {

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    // POST, not GET: every call is a new paid request to the model and gives a
    // new answer, so it must never be cached, prefetched or retried by a proxy.
    // No body: the server decides what is sent, the client cannot add to it.
    @PostMapping("/review")
    public ResponseEntity<CoachReviewResponse> review(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!userId.equals(((Number) jwt.getClaim("uid")).longValue())) {
            throw new AccessDeniedException("access denied");
        }
        return ResponseEntity.ok(coachService.review(userId));
    }
}
