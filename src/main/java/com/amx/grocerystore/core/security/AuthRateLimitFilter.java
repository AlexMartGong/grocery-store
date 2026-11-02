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
 * Per-IP token-bucket rate limiter for the unauthenticated auth endpoints (VULN-04 brute-force
 * mitigation). Sits ahead of {@link JwtAuthenticationFilter} in the chain. Two independent budgets:
 * <ul>
 *   <li>login (store + admin share one bucket per IP): 10 attempts/minute;</li>
 *   <li>store-user refresh: 30 attempts/minute — looser, a healthy client refreshes about once an hour,
 *       and refresh traffic must never eat the login budget.</li>
 * </ul>
 * On overflow it short-circuits with 429 and never touches the chain. The key is
 * {@code request.getRemoteAddr()}, which is the real client IP because
 * {@code server.forward-headers-strategy=native} lets Tomcat's RemoteIpValve honour
 * {@code X-Forwarded-For} from internal proxies (Caddy) only.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LOGIN_PATHS = Set.of("/api/v1/auth/login", "/api/v1/admin/login");
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    static final int MAX_LOGIN_ATTEMPTS_PER_MINUTE = 10;
    static final int MAX_REFRESH_ATTEMPTS_PER_MINUTE = 30;

    private final Map<String, Bucket> loginBucketsByIp = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBucketsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !HttpMethod.POST.matches(request.getMethod())
                || path == null
                || !(LOGIN_PATHS.contains(path) || REFRESH_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Bucket bucket = REFRESH_PATH.equals(request.getServletPath())
                ? refreshBucketsByIp.computeIfAbsent(request.getRemoteAddr(),
                        ip -> newBucket(MAX_REFRESH_ATTEMPTS_PER_MINUTE))
                : loginBucketsByIp.computeIfAbsent(request.getRemoteAddr(),
                        ip -> newBucket(MAX_LOGIN_ATTEMPTS_PER_MINUTE));
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            rejectTooManyRequests(response);
        }
    }

    private static Bucket newBucket(int attemptsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(attemptsPerMinute)
                .refillGreedy(attemptsPerMinute, Duration.ofMinutes(1))
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
