package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionPlanResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionPlanController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SubscriptionPlanControllerTest {

    private static final String URL = "/api/v1/admin/plans";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

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

    private SubscriptionPlanResponse plan(String code, String monthlyFee, String yearlyFee,
                                          int maxStations, boolean notifications, boolean invoicing) {
        return new SubscriptionPlanResponse(
                UUID.randomUUID(), code, code, new BigDecimal(monthlyFee), new BigDecimal(yearlyFee),
                BigDecimal.ZERO, maxStations, notifications, invoicing, code + " tier");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void get_asSuperAdmin_returnsTiersWithEntitlements() throws Exception {
        given(subscriptionService.listPlans()).willReturn(List.of(
                plan("STANDARD", "299.00", "2990.00", 1, false, false),
                plan("PRO", "499.00", "4990.00", 2, true, false),
                plan("BUSINESS", "899.00", "8990.00", 5, true, true)));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[0].monthly_fee").value(299.00))
                .andExpect(jsonPath("$[0].yearly_fee").value(2990.00))
                .andExpect(jsonPath("$[0].max_stations").value(1))
                .andExpect(jsonPath("$[0].includes_notifications").value(false))
                .andExpect(jsonPath("$[2].code").value("BUSINESS"))
                .andExpect(jsonPath("$[2].max_stations").value(5))
                .andExpect(jsonPath("$[2].includes_invoicing").value(true));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void get_asOwner_returns403() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void get_asCashier_returns403() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
