package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.stores.application.StoreStationService;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStoreStationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminStoreStationControllerTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final String URL = "/api/v1/admin/stores/" + STORE_ID + "/stations";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StoreStationService storeStationService;

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

    private StoreStationResponse sampleResponse(UUID id) {
        return new StoreStationResponse(id, "Caja 1", null, StoreStationStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_asSuperAdmin_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeStationService.createStation(eq(STORE_ID), eq("Caja 1")))
                .willReturn(sampleResponse(id));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caja 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Caja 1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_blankName_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_asOwner_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caja 1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_asCashier_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caja 1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caja 1"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
