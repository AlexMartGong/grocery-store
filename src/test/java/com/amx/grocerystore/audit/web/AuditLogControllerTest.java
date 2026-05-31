package com.amx.grocerystore.audit.web;

import com.amx.grocerystore.audit.application.AuditLogService;
import com.amx.grocerystore.audit.web.dto.AuditLogResponse;
import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.dto.PageResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuditLogControllerTest {

    private static final String URL = "/api/v1/audit-logs";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditLogService auditLogService;

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

    private AuditLogResponse sample() {
        return new AuditLogResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Alex Martinez",
                "PRICE_CHANGED",
                "PRODUCT",
                UUID.randomUUID(),
                Map.of("oldPrice", "20.00", "newPrice", "24.50"),
                OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void list_asOwner_returns200() throws Exception {
        given(auditLogService.getStoreAuditLogs(any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(sample()), 0, 20, 1L, 1));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.content[0].userName").value("Alex Martinez"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void list_asCashier_returns403() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }
}
