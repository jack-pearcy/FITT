package com.fitt.coach.dto;

import java.time.Instant;

// One coaching review. Nothing here is stored: it is built for this response only.
public record CoachReviewResponse(
        String review,
        int sessionsReviewed,
        Instant generatedAt
) {}
