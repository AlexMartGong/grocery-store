package com.amx.grocerystore.users.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final String URL = "/api/v1/users";

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
        return new UserResponse(id, "Cajero", "cashier1", UserRole.CASHIER, true, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_returns201_withoutPasswordHashOrStoreId() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.create(any())).willReturn(sample(id));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"Secret12","role":"CASHIER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("cashier1"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.storeId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_shortPassword_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"short","role":"CASHIER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_blankUsername_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"","password":"Secret12","role":"CASHIER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_missingRole_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"Secret12"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").exists());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_duplicateUsername_returns409() throws Exception {
        given(userService.create(any())).willThrow(DuplicateResourceException.username("cashier1"));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"Secret12","role":"CASHIER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("username_taken"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void get_byId_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.findById(id)).willReturn(sample(id));

        mvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void get_missing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.findById(id)).willThrow(NotFoundException.user(id));

        mvc.perform(get(URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user_not_found"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void list_paginated_returns200() throws Exception {
        given(userService.list(eq(null), eq(null), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(sample(UUID.randomUUID())), 0, 20, 1L, 1));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void list_withFilters_returns200() throws Exception {
        given(userService.list(eq(true), eq(UserRole.CASHIER), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

        mvc.perform(get(URL).param("active", "true").param("role", "CASHIER"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_withPassword_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.update(eq(id), any())).willReturn(sample(id));

        mvc.perform(put(URL + "/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nuevo","password":"NewPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_withoutPassword_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.update(eq(id), any())).willReturn(sample(id));

        mvc.perform(put(URL + "/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nuevo"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void delete_firstAndRepeat_bothReturn204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).softDelete(id);

        mvc.perform(delete(URL + "/" + id).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(URL + "/" + id).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_withSuperAdminRole_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"Secret12","role":"CASHIER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Cajero","username":"cashier1","password":"Secret12","role":"CASHIER"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
