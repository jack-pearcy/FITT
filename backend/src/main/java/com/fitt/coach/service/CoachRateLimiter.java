package com.fitt.coach.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Caps how many reviews one user can ask for in 24 hours, because every review
// is a paid request to the model. A sliding window per user id.
//
// In memory and per instance, like AuthRateLimitFilter: right for one backend
// container, and a restart hands everyone a fresh allowance.
@Component
public class CoachRateLimiter {

    private static final long WINDOW_MILLIS = 24 * 60 * 60 * 1000L;

    private final int dailyLimit;
    private final Clock clock;
    private final Map<Long, Deque<Long>> reviewsByUser = new ConcurrentHashMap<>();

    @Autowired
    public CoachRateLimiter(CoachProperties properties) {
        this(properties.getDailyLimit(), Clock.systemUTC());
    }

    CoachRateLimiter(int dailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    // Returns 0 when the review may go ahead (and counts it), otherwise the number
    // of seconds until the user may ask again.
    public long tryAcquire(Long userId) {
        long now = clock.millis();
        Deque<Long> reviews = reviewsByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (reviews) {
            while (!reviews.isEmpty() && now - reviews.peekFirst() >= WINDOW_MILLIS) {
                reviews.pollFirst();
            }
            if (reviews.size() >= dailyLimit) {
                return Math.max(1, (WINDOW_MILLIS - (now - reviews.peekFirst()) + 999) / 1000);
            }
            reviews.addLast(now);
            return 0;
        }
    }
}
