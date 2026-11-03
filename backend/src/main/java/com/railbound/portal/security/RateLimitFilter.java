package com.railbound.portal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter for abuse-prone endpoints.
 *
 * <p>Deliberately simple: this is a single-node guard for login, checkout,
 * purchase and refund endpoints. A multi-node deployment should replace the
 * {@code ConcurrentHashMap} with Redis so limits are shared across instances.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private final int authPerMinute;
    private final int authBurst;
    private final int checkoutPerMinute;
    private final int checkoutBurst;
    private final int writePerMinute;
    private final int writeBurst;

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.auth-per-minute:30}") int authPerMinute,
            @Value("${app.security.rate-limit.auth-burst:15}") int authBurst,
            @Value("${app.security.rate-limit.checkout-per-minute:30}") int checkoutPerMinute,
            @Value("${app.security.rate-limit.checkout-burst:10}") int checkoutBurst,
            @Value("${app.security.rate-limit.write-per-minute:120}") int writePerMinute,
            @Value("${app.security.rate-limit.write-burst:40}") int writeBurst) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.authBurst = authBurst;
        this.checkoutPerMinute = checkoutPerMinute;
        this.checkoutBurst = checkoutBurst;
        this.writePerMinute = writePerMinute;
        this.writeBurst = writeBurst;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Rule rule = ruleFor(request);
        if (!enabled || rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String client = clientKey(request);
        String bucketKey = rule.name + ":" + client;
        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                key -> new Bucket(rule.perMinute, rule.burst));
        if (!bucket.tryConsume()) {
            String correlationId = UUID.randomUUID().toString();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            objectMapper.writeValue(response.getWriter(), new ApiError(
                    "RATE_LIMITED",
                    "Too many requests. Please wait a moment and try again.",
                    correlationId));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Only state-changing or credential-handling endpoints are limited. Reads
     * (catalog, wallet) stay unthrottled so browsing never fails for a player.
     */
    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/v1/auth/")) {
            return new Rule("auth", authPerMinute, authBurst);
        }
        if ("POST".equals(method) && path.startsWith("/api/v1/payments/checkout-sessions")) {
            return new Rule("checkout", checkoutPerMinute, checkoutBurst);
        }
        if ("POST".equals(method) && path.startsWith("/api/v1/shop/purchases")) {
            return new Rule("purchase", writePerMinute, writeBurst);
        }
        if ("POST".equals(method) && path.startsWith("/api/v1/admin/refunds")) {
            return new Rule("refund", writePerMinute, writeBurst);
        }
        return null;
    }

    private String clientKey(HttpServletRequest request) {
        // Prefer the authenticated user so one shared NAT cannot lock out a whole
        // building; fall back to the socket address for anonymous calls.
        Object principal = SecurityUtils.getCurrentUser();
        if (principal instanceof SecurityUser user) {
            return "user:" + user.getUserId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private record Rule(String name, int perMinute, int burst) {
    }

    /**
     * Classic token bucket. Synchronised because a single bucket can be touched
     * by concurrent requests; contention is per client, not global.
     */
    static final class Bucket {
        private final double refillPerNano;
        private final double capacity;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int perMinute, int burst) {
            this.capacity = Math.max(1, burst);
            this.tokens = this.capacity;
            this.refillPerNano = Math.max(1, perMinute) / 60_000_000_000.0;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
            lastRefillNanos = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
