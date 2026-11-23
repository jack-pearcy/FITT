package com.fitt.shared.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Slows down password guessing, account spam, and refresh token guessing on the
// public auth endpoints, and password guessing on the routes that ask a signed in
// user for the password again. Logout is public too and looks a refresh token up,
// so it is limited with the rest.
// A sliding window per client address: more than maxAttempts POSTs inside the
// window gets a 429 with Retry-After until the oldest attempt ages out.
//
// In memory and per instance, which is the right size for the current single
// backend container. A second instance would need a shared store such as Redis.
// Off by default so tests and local dev are not throttled; the prod profile turns
// it on. Behind Caddy the real client address arrives through X-Forwarded-For,
// which server.forward-headers-strategy=native resolves into getRemoteAddr().
@Component
@ConditionalOnProperty(name = "fitt.rate-limit.auth.enabled", havingValue = "true")
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/identity/auth/login", "/api/identity/auth/register", "/api/identity/auth/refresh",
                    "/api/identity/auth/logout");

    // Above this many tracked clients, drop the idle ones so the map cannot grow
    // without bound under a flood of distinct addresses.
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> attemptsByClient = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(
            @Value("${fitt.rate-limit.auth.max-attempts:10}") int maxAttempts,
            @Value("${fitt.rate-limit.auth.window-seconds:60}") long windowSeconds) {
        this(maxAttempts, windowSeconds, Clock.systemUTC());
    }

    // Package-private so a test can drive time without sleeping.
    AuthRateLimitFilter(int maxAttempts, long windowSeconds, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000;
        this.clock = clock;
    }

    // Signed in routes that take the password again. The user id is in the middle
    // of the path, so these are matched by their ending.
    private static final Set<String> LIMITED_SUFFIXES = Set.of("/identity/account/delete", "/identity/account/password");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !isLimited(decodedPath(request));
    }

    // The path the way the router reads it. getRequestURI() is the raw text of the
    // request line, so /api/identity/auth/%6Cogin does not equal the login path
    // here, yet Spring decodes it and routes it to login. Matching on the raw text
    // let one encoded letter skip the limit. Spring Security's firewall already
    // refuses an encoded slash, an encoded percent sign and a semicolon, so one
    // decode gives the same path the router sees.
    private static String decodedPath(HttpServletRequest request) {
        try {
            return UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            // Not a path any route answers. Counting it costs nothing and never
            // lets a broken encoding slip past the limit.
            return LIMITED_PATHS.iterator().next();
        }
    }

    private static boolean isLimited(String path) {
        return LIMITED_PATHS.contains(path)
                || (path.startsWith("/api/users/") && LIMITED_SUFFIXES.stream().anyMatch(path::endsWith));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        long now = clock.millis();
        long retryAfterSeconds = tryAcquire(request.getRemoteAddr(), now);

        if (retryAfterSeconds > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"Too many attempts, try again later\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // Returns 0 when the attempt is allowed (and records it), otherwise the number
    // of seconds until the client may try again.
    long tryAcquire(String client, long now) {
        if (attemptsByClient.size() > SWEEP_THRESHOLD) {
            attemptsByClient.values().removeIf(d -> isIdle(d, now));
        }

        Deque<Long> attempts = attemptsByClient.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() >= windowMillis) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                long waitMillis = windowMillis - (now - attempts.peekFirst());
                return Math.max(1, (waitMillis + 999) / 1000);
            }
            attempts.addLast(now);
            return 0;
        }
    }

    private boolean isIdle(Deque<Long> attempts, long now) {
        synchronized (attempts) {
            return attempts.isEmpty() || now - attempts.peekLast() >= windowMillis;
        }
    }
}
