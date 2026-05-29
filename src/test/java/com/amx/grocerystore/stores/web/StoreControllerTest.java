package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.web.dto.StoreResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StoreControllerTest {

    private static final String URL = "/api/v1/stores";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StoreService storeService;

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

    private StoreResponse sampleResponse(UUID id) {
        return new StoreResponse(id, "tienda-demo", "Tienda", null, null, null, null, null,
                StoreStatus.ACTIVE, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_returns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeService.create(any())).willReturn(sampleResponse(id));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/stores/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("tienda-demo"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_withNotificationFields_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeService.create(any())).willReturn(sampleResponse(id));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda","telegramChatId":"123","preferredAdminChannel":"TELEGRAM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_invalidChannel_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda","preferredAdminChannel":"SMS"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_invalidCodePattern_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAD CODE","name":"Tienda"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.code").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_blankName_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_duplicateCode_returns409() throws Exception {
        given(storeService.create(any())).willThrow(DuplicateResourceException.storeCode("tienda-demo"));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("store_code_taken"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void get_byId_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeService.findById(id)).willReturn(sampleResponse(id));

        mvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void get_missing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeService.findById(id)).willThrow(NotFoundException.store(id));

        mvc.perform(get(URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("store_not_found"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void list_paginated_returns200() throws Exception {
        given(storeService.list(eq(null), any(), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(sampleResponse(UUID.randomUUID())), 0, 20, 1L, 1));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void list_withStatusFilter_returns200() throws Exception {
        given(storeService.list(eq(StoreStatus.CANCELED), any(), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

        mvc.perform(get(URL).param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void put_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(storeService.update(eq(id), any())).willReturn(sampleResponse(id));

        mvc.perform(put(URL + "/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nuevo","ownerName":null,"whatsapp":null,"address":null,"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void put_invalidBody_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put(URL + "/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","status":"ACTIVE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void delete_firstCall_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(storeService).softDelete(id);

        mvc.perform(delete(URL + "/" + id).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void delete_repeatCall_alsoReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(storeService).softDelete(id);

        mvc.perform(delete(URL + "/" + id).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(URL + "/" + id).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_withOwnerRole_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"tienda-demo","name":"Tienda"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
