package com.amx.grocerystore.users.web;

import com.amx.grocerystore.core.security.AccountInactiveException;
import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.exception.RefreshTokenException;
import com.amx.grocerystore.users.application.AuthService;
import com.amx.grocerystore.users.application.AuthSession;
import com.amx.grocerystore.users.web.dto.LoginResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RefreshTokenCookieFactory.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private static final String URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String COOKIE = RefreshTokenCookieFactory.COOKIE_NAME;
    private static final String ALLOWED_ORIGIN = "http://localhost:5173"; // src/test/resources
    private static final long SIXTY_DAYS_SECONDS = 60L * 24 * 60 * 60;

    private static final String VALID_LOGIN = """
            {"storeCode":"tienda-demo","username":"owner","password":"owner123"}
            """;

    @BeforeEach
    void passThroughFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0, HttpServletRequest.class),
                              inv.getArgument(1, HttpServletResponse.class));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static AuthSession session(String accessToken, String refreshToken) {
        return new AuthSession(new LoginResponse(accessToken, "Bearer", 3600L), refreshToken);
    }

    private static void assertRefreshCookieIssued(MvcResult result, String expectedValue) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=" + expectedValue + ";")
                .contains("Path=/api/v1/auth", "Max-Age=" + SIXTY_DAYS_SECONDS,
                        "HttpOnly", "Secure", "SameSite=Lax")
                .doesNotContain("Domain=");
    }

    private static void assertRefreshCookieCleared(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=;")
                .contains("Path=/api/v1/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax");
    }

    // ── login ───────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken_andPersistentRefreshCookie() throws Exception {
        given(authService.login(any(), any())).willReturn(session("jwt.token.here", "raw-refresh"));

        MvcResult result = mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertRefreshCookieIssued(result, "raw-refresh");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("raw-refresh");
        verify(authService).login(any(), eq("Mozilla/5.0"));
    }

    @Test
    void login_badCredentials_returns401_noCookie() throws Exception {
        given(authService.login(any(), any())).willThrow(new BadCredentialsException("invalid_credentials"));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeCode":"tienda-demo","username":"owner","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void login_storeInactive_returns403() throws Exception {
        given(authService.login(any(), any())).willThrow(new DisabledException("store_inactive"));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("store_inactive"));
    }

    @Test
    void login_blankStoreCode_returns400WithField() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeCode":"","username":"owner","password":"owner123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.storeCode").exists());
    }

    @Test
    void login_blankUsername_returns400WithField() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeCode":"tienda-demo","username":"","password":"owner123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.username").exists());
    }

    @Test
    void login_missingPassword_returns400WithField() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeCode":"tienda-demo","username":"owner"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void login_storeCodeExceedsMaxLength_returns400WithField() throws Exception {
        String longCode = "a".repeat(31);

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeCode":"%s","username":"owner","password":"owner123"}
                                """.formatted(longCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.storeCode").exists());
    }

    // ── refresh ─────────────────────────────────────────────────────────────────

    @Test
    void refresh_validCookie_isPublic_returnsNewAccessToken_andRotatedCookie() throws Exception {
        given(authService.refresh(eq("old-raw"), any())).willReturn(session("fresh.jwt", "new-raw"));

        MvcResult result = mvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE, "old-raw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fresh.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andReturn();

        assertRefreshCookieIssued(result, "new-raw");
    }

    @Test
    void refresh_withoutCookie_returns401InvalidRefreshToken() throws Exception {
        mvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_refresh_token"));

        verifyNoInteractions(authService);
    }

    @Test
    void refresh_expiredOrUnknown_returns401InvalidRefreshToken() throws Exception {
        given(authService.refresh(eq("stale"), any())).willThrow(RefreshTokenException.invalid());

        mvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE, "stale")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_refresh_token"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refresh_reusedToken_returns401RefreshTokenReused() throws Exception {
        given(authService.refresh(eq("rotated"), any())).willThrow(RefreshTokenException.reused());

        mvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE, "rotated")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("refresh_token_reused"));
    }

    @Test
    void refresh_userDeactivated_returns401UserInactive() throws Exception {
        given(authService.refresh(eq("old-raw"), any())).willThrow(new AccountInactiveException("user_inactive"));

        mvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE, "old-raw")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("user_inactive"));
    }

    @Test
    void refresh_storeSuspended_returns401StoreInactive() throws Exception {
        given(authService.refresh(eq("old-raw"), any())).willThrow(new AccountInactiveException("store_inactive"));

        mvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE, "old-raw")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("store_inactive"));
    }

    // ── logout ──────────────────────────────────────────────────────────────────

    @Test
    void logout_withCookie_isPublic_revokesAndClearsCookie() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT_URL).cookie(new Cookie(COOKIE, "current")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertRefreshCookieCleared(result);
        verify(authService).logout("current");
    }

    @Test
    void logout_withoutCookie_isIdempotent_stillClearsCookie() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        assertRefreshCookieCleared(result);
        verify(authService).logout(isNull());
    }

    // ── CORS (credentials) ──────────────────────────────────────────────────────

    @Test
    void refresh_preflightFromAllowedOrigin_allowsCredentials() throws Exception {
        mvc.perform(options(REFRESH_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void refresh_fromAllowedOrigin_echoesOriginWithCredentials() throws Exception {
        given(authService.refresh(eq("old-raw"), any())).willReturn(session("fresh.jwt", "new-raw"));

        mvc.perform(post(REFRESH_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(new Cookie(COOKIE, "old-raw")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void refresh_fromForeignOrigin_rejectedBeforeController() throws Exception {
        mvc.perform(post(REFRESH_URL)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(COOKIE, "old-raw")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }
}
