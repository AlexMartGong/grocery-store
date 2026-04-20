package com.amx.grocerystore.admin.web;

import com.amx.grocerystore.admin.application.AdminAuthService;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.users.web.dto.LoginResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAuthController.class)
@Import(SecurityConfig.class)
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String URL = "/api/v1/admin/login";

    @BeforeEach
    void passThroughFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0, HttpServletRequest.class),
                              inv.getArgument(1, HttpServletResponse.class));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        given(adminAuthService.login(any())).willReturn(new LoginResponse("jwt.token.here", "Bearer", 28800L));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alex","password":"alex123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(28800));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        given(adminAuthService.login(any())).willThrow(new BadCredentialsException("invalid_credentials"));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alex","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void login_blankUsername_returns400WithField() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"alex123"}
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
                                {"username":"alex"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void login_usernameExceedsMaxLength_returns400WithField() throws Exception {
        String longUsername = "a".repeat(51);

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"alex123"}
                                """.formatted(longUsername)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.username").exists());
    }
}
