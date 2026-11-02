package com.amx.grocerystore.core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ADMIN_LOGIN = "/api/v1/admin/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(JsonMapper.builder().build());
    private final AtomicInteger passed = new AtomicInteger();
    private final FilterChain chain = (req, res) -> passed.incrementAndGet();

    private MockHttpServletResponse post(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void refresh_allows30PerMinute_then429WithRetryAfter() throws Exception {
        for (int i = 0; i < AuthRateLimitFilter.MAX_REFRESH_ATTEMPTS_PER_MINUTE; i++) {
            assertThat(post(REFRESH, "10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = post(REFRESH, "10.0.0.1");

        assertThat(passed).hasValue(30);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("too_many_requests");
    }

    @Test
    void refreshBudget_isSeparateFromLoginBudget() throws Exception {
        for (int i = 0; i <= AuthRateLimitFilter.MAX_REFRESH_ATTEMPTS_PER_MINUTE; i++) {
            post(REFRESH, "10.0.0.2");
        }

        assertThat(post(LOGIN, "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void login_unchanged_10PerMinuteSharedByStoreAndAdminLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            post(LOGIN, "10.0.0.3");
            post(ADMIN_LOGIN, "10.0.0.3");
        }

        assertThat(post(LOGIN, "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(post(ADMIN_LOGIN, "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(post(REFRESH, "10.0.0.3").getStatus())
                .as("exhausted login budget must not block session renewal")
                .isEqualTo(200);
    }

    @Test
    void bucketsArePerClientIp() throws Exception {
        for (int i = 0; i <= AuthRateLimitFilter.MAX_REFRESH_ATTEMPTS_PER_MINUTE; i++) {
            post(REFRESH, "10.0.0.4");
        }

        assertThat(post(REFRESH, "10.0.0.5").getStatus()).isEqualTo(200);
    }

    @Test
    void logout_isNotRateLimited() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertThat(post(LOGOUT, "10.0.0.6").getStatus()).isEqualTo(200);
        }
        assertThat(passed).hasValue(100);
    }
}
