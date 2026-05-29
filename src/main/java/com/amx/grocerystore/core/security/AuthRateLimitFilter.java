package com.amx.grocerystore.core.security;

import com.amx.grocerystore.core.web.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP token-bucket rate limiter for the unauthenticated login endpoints (VULN-04 brute-force
 * mitigation). Sits ahead of {@link JwtAuthenticationFilter} in the chain. Caps each remote address
 * at 10 attempts/minute; on overflow it short-circuits with 429 and never touches the chain.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LOGIN_PATHS = Set.of("/api/v1/auth/login", "/api/v1/admin/login");
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !LOGIN_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Bucket bucket = bucketsByIp.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            rejectTooManyRequests(response);
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(MAX_ATTEMPTS_PER_MINUTE)
                .refillGreedy(MAX_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void rejectTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of("too_many_requests"));
    }
}
