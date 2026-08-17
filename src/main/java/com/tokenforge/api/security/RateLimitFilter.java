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
            new LimitedRoute("POST", "/auth/verify-email", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/auth/forgot-password", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/auth/resend-verification", 5, Duration.ofMinutes(15)),
            new LimitedRoute("POST", "/bugs", 3, Duration.ofHours(1)),
    };

    private static final String ATTR_PROCESSED = "com.tokenforge.rateLimitProcessed";

    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

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
        Bucket bucket = buckets.computeIfAbsent(bucketKey, key -> newBucket(route));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        respondTooManyRequests(response);
    }

    private LimitedRoute matchRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        for (LimitedRoute route : LIMITED_ROUTES) {
            if (route.method().equalsIgnoreCase(request.getMethod()) && route.path().equals(path)) {
                return route;
            }
        }
        return null;
    }

    private Bucket newBucket(LimitedRoute route) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(route.capacity())
                .refillIntervally(route.capacity(), route.period())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
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
