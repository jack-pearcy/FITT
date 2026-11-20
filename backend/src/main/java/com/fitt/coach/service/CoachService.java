package com.fitt.coach.service;

import com.fitt.coach.dto.CoachReviewResponse;
import com.fitt.coach.service.exception.CoachLimitException;
import com.fitt.coach.service.exception.CoachUnavailableException;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.service.GoalService;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.service.WorkoutSessionService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

// Builds one coaching review from the user's recent training.
//
// Privacy rules this class and CoachPromptBuilder keep:
// - only the fields CoachPromptBuilder writes leave the server;
// - nothing is stored: not the text sent, not the review;
// - neither is logged.
//
// No transaction here on purpose. The reads finish in their own short
// transactions, so no database connection is held during the slow model call.
@Service
public class CoachService {

    private final WorkoutSessionService sessionService;
    private final GoalService goalService;
    private final CoachModel model;
    private final CoachRateLimiter rateLimiter;
    private final CoachProperties properties;
    private final Clock clock = Clock.systemUTC();

    public CoachService(
            WorkoutSessionService sessionService,
            GoalService goalService,
            CoachModel model,
            CoachRateLimiter rateLimiter,
            CoachProperties properties) {
        this.sessionService = sessionService;
        this.goalService = goalService;
        this.model = model;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public CoachReviewResponse review(Long userId) {
        if (!model.isAvailable()) {
            throw new CoachUnavailableException("The coach is not set up on this server");
        }

        List<WorkoutSessionResponse> recent = sessionService.listSessions(userId).stream()
                .filter(session -> session.finishedAt() != null)
                .sorted(Comparator.comparing(WorkoutSessionResponse::finishedAt).reversed())
                .limit(properties.getSessionsToReview())
                .toList();
        if (recent.isEmpty()) {
            throw new IllegalArgumentException("Finish at least one workout before asking the coach");
        }

        // Checked last, so a request that could never reach the model does not use
        // up one of the day's reviews.
        long retryAfterSeconds = rateLimiter.tryAcquire(userId);
        if (retryAfterSeconds > 0) {
            throw new CoachLimitException(retryAfterSeconds);
        }

        GoalDirection goal = goalService.hasGoals(userId) ? goalService.getGoals(userId).goalDirection() : null;
        Instant now = clock.instant();
        String summary = CoachPromptBuilder.trainingSummary(recent, goal, now);

        String review = model.review(CoachPromptBuilder.INSTRUCTIONS, summary);
        return new CoachReviewResponse(review, recent.size(), now);
    }
}
