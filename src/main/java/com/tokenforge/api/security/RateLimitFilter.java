package com.tokenforge.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokenforge.api.errorhandler.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Limita a taxa de requisições (por IP) nos endpoints anônimos mais sensíveis a abuso:
 * login, registro, verificação de e-mail, recuperação de senha e envio de bug report.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record LimitedRoute(String method, String path, int capacity, Duration period) {}

    private static final LimitedRoute[] LIMITED_ROUTES = {
            new LimitedRoute("POST", "/auth/login", 5, Duration.ofMinutes(1)),
            new LimitedRoute("POST", "/auth/register", 5, Duration.ofMinutes(1)),
            new LimitedRoute("POST", "/auth/google", 10, Duration.ofMinutes(1)),
            new LimitedRoute("POST", "/auth/verify-email", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/auth/forgot-password", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/auth/resend-verification", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/auth/reset-password", 5, Duration.ofMinutes(15)),
            new LimitedRoute("PUT", "/auth/profile", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/bugs", 3, Duration.ofHours(1)),
            new LimitedRoute("POST", "/tokens", 20, Duration.ofMinutes(1)),
            new LimitedRoute("PUT", "/tokens/*", 30, Duration.ofMinutes(1)),
            new LimitedRoute("DELETE", "/tokens/*", 30, Duration.ofMinutes(1)),
            new LimitedRoute("POST", "/uploads/signature", 20, Duration.ofMinutes(1)),
    };

    private static final String ATTR_PROCESSED = "com.tokenforge.rateLimitProcessed";

    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    private static final class TrackedBucket {
        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private TrackedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessNanos = System.nanoTime();
        }
    }

    private final ObjectMapper objectMapper;
    private final Map<String, TrackedBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean cleaningUp = new AtomicBoolean(false);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getAttribute(ATTR_PROCESSED) != null) {
            filterChain.doFilter(request, response);
            return;
        }
        request.setAttribute(ATTR_PROCESSED, Boolean.TRUE);

        LimitedRoute route = matchRoute(request);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = route.method() + " " + route.path() + "|" + clientIp(request);
        evictIdleBucketsIfNeeded();

        TrackedBucket tracked = buckets.computeIfAbsent(bucketKey, key -> new TrackedBucket(newBucket(route)));
        tracked.lastAccessNanos = System.nanoTime();

        if (tracked.bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        respondTooManyRequests(response);
    }

    private LimitedRoute matchRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        for (LimitedRoute route : LIMITED_ROUTES) {
            if (route.method().equalsIgnoreCase(request.getMethod()) && pathMatches(route.path(), path)) {
                return route;
            }
        }
        return null;
    }

    private boolean pathMatches(String routePath, String requestPath) {
        if (routePath.endsWith("/*")) {
            return requestPath.startsWith(routePath.substring(0, routePath.length() - 1));
        }
        return routePath.equals(requestPath);
    }

    private void evictIdleBucketsIfNeeded() {
        if (buckets.size() < CLEANUP_THRESHOLD || !cleaningUp.compareAndSet(false, true)) {
            return;
        }
        try {
            long cutoff = System.nanoTime() - IDLE_TTL.toNanos();
            buckets.values().removeIf(tracked -> tracked.lastAccessNanos < cutoff);
        } finally {
            cleaningUp.set(false);
        }
    }

    private Bucket newBucket(LimitedRoute route) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(route.capacity())
                .refillIntervally(route.capacity(), route.period())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Usa o ÚLTIMO IP da cadeia X-Forwarded-For, não o primeiro: o primeiro trecho é
     * escrito pelo próprio cliente e pode ser forjado livremente (bastaria enviar um
     * valor diferente a cada requisição para burlar o limite). O último trecho é o que
     * o proxy confiável da plataforma (Render/Railway/etc.) anexa com o IP real da
     * conexão, e não pode ser manipulado pelo lado do cliente.
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Muitas tentativas em pouco tempo. Aguarde alguns instantes e tente novamente.",
                LocalDateTime.now(),
                null
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
