package com.amx.grocerystore.users.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.users.application.UserService;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.web.dto.UserResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserProvisioningController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminUserProvisioningControllerTest {

    private static final UUID STORE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String URL = "/api/v1/admin/stores/" + STORE_ID + "/users";
    private static final String VALID_BODY = """
            {"fullName":"Dueño Uno","username":"owner1","password":"Secret12","role":"OWNER"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @BeforeEach
    void passThroughFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0, HttpServletRequest.class),
                    inv.getArgument(1, HttpServletResponse.class));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            ((HttpServletResponse) inv.getArgument(1)).sendError(401);
            return null;
        }).when(jwtAuthenticationEntryPoint).commence(any(), any(), any());
    }

    private UserResponse sample(UUID id) {
        return new UserResponse(id, "Dueño Uno", "owner1", UserRole.OWNER, true, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_asSuperAdmin_returns201_withLocationAndNoPasswordHash() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.provisionUserForStore(eq(STORE_ID), any())).willReturn(sample(userId));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/admin/stores/" + STORE_ID + "/users/" + userId))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("owner1"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.storeId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_asOwner_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_asCashier_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_storeNotFound_returns404() throws Exception {
        given(userService.provisionUserForStore(eq(STORE_ID), any()))
                .willThrow(NotFoundException.store(STORE_ID));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("store_not_found"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_storeNotActive_returns409() throws Exception {
        given(userService.provisionUserForStore(eq(STORE_ID), any()))
                .willThrow(InvalidResourceStateException.storeNotActive(STORE_ID));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("store_not_active"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_ownerAlreadyExists_returns409() throws Exception {
        given(userService.provisionUserForStore(eq(STORE_ID), any()))
                .willThrow(DuplicateResourceException.storeOwner(STORE_ID));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("store_owner_exists"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_missingRole_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Dueño","username":"owner1","password":"Secret12"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").exists());
    }
}
