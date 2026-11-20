package com.fitt.coach.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CoachRateLimiterTest {

    private static class MutableClock extends Clock {
        private long millis = 1_000_000L;
        void advanceHours(long hours) { millis += hours * 3_600_000L; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    @Test
    void reviewsOverTheDailyLimitAreRefusedWithTheWaitInSeconds() {
        CoachRateLimiter limiter = new CoachRateLimiter(2, new MutableClock());

        assertThat(limiter.tryAcquire(1L)).isZero();
        assertThat(limiter.tryAcquire(1L)).isZero();

        assertThat(limiter.tryAcquire(1L)).isEqualTo(86_400);
    }

    @Test
    void eachUserHasTheirOwnAllowance() {
        CoachRateLimiter limiter = new CoachRateLimiter(1, new MutableClock());

        assertThat(limiter.tryAcquire(1L)).isZero();

        assertThat(limiter.tryAcquire(1L)).isPositive();
        assertThat(limiter.tryAcquire(2L)).isZero();
    }

    @Test
    void theAllowanceComesBackOnceAReviewIsADayOld() {
        MutableClock clock = new MutableClock();
        CoachRateLimiter limiter = new CoachRateLimiter(1, clock);
        limiter.tryAcquire(1L);

        clock.advanceHours(23);
        assertThat(limiter.tryAcquire(1L)).isEqualTo(3_600);

        clock.advanceHours(1);
        assertThat(limiter.tryAcquire(1L)).isZero();
    }
}
