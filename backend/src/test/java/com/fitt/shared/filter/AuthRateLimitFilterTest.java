package com.fitt.shared.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the filter with a fixed clock so the sliding window can be tested
// without sleeping.
class AuthRateLimitFilterTest {

    private static final String LOGIN = "/api/identity/auth/login";

    private static class MutableClock extends Clock {
        private long millis = 1_000_000L;
        void advanceSeconds(long s) { millis += s * 1000; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    private MockHttpServletResponse call(AuthRateLimitFilter filter, String method, String path, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void attemptsOverTheLimitInsideTheWindow_get429WithRetryAfter() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3, 60, new MutableClock());

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call(filter, "POST", LOGIN, "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void windowSlides_allowsAgainOnceOldAttemptsAgeOut() throws Exception {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(2, 60, clock);

        call(filter, "POST", LOGIN, "1.1.1.1");
        call(filter, "POST", LOGIN, "1.1.1.1");
        assertThat(call(filter, "POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(429);

        clock.advanceSeconds(61);

        assertThat(call(filter, "POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void eachClientAddressHasItsOwnBudget() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 60, new MutableClock());

        call(filter, "POST", LOGIN, "1.1.1.1");

        assertThat(call(filter, "POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(call(filter, "POST", LOGIN, "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void otherPathsAndMethodsAreNeverCounted() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 60, new MutableClock());

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "GET", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
            assertThat(call(filter, "POST", "/api/users/1/workout-plans", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
    }
}
