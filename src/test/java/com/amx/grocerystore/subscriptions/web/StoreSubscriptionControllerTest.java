package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreSubscriptionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StoreSubscriptionControllerTest {

    private static final UUID STORE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String URL = "/api/v1/admin/stores/" + STORE_ID + "/subscription";
    private static final String VALID_BODY = """
            {"plan_code":"PRO","billing_cycle":"YEARLY"}
            """;

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

    private SubscriptionResponse sample() {
        return new SubscriptionResponse(
                UUID.randomUUID(), "PRO", "Pro", BillingCycle.YEARLY, new BigDecimal("4990.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2027, 8, 10), SubscriptionStatus.ACTIVE,
                2, true, false);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void get_asSuperAdmin_returnsCurrentSubscription() throws Exception {
        given(subscriptionService.getActiveSubscription(STORE_ID)).willReturn(sample());

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_code").value("PRO"))
                .andExpect(jsonPath("$.plan_name").value("Pro"))
                .andExpect(jsonPath("$.billing_cycle").value("YEARLY"))
                .andExpect(jsonPath("$.amount_due").value(4990.00))
                .andExpect(jsonPath("$.start_date").value("2026-08-10"))
                .andExpect(jsonPath("$.next_billing_date").value("2027-08-10"))
                .andExpect(jsonPath("$.includes_notifications").value(true))
                .andExpect(jsonPath("$.includes_invoicing").value(false));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void get_storeWithoutSubscription_returns404() throws Exception {
        given(subscriptionService.getActiveSubscription(STORE_ID))
                .willThrow(NotFoundException.subscription(STORE_ID));

        mvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("subscription_not_found"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void get_asOwner_returns403() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());

        verify(subscriptionService, never()).getActiveSubscription(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_asSuperAdmin_validBody_returns201() throws Exception {
        given(subscriptionService.changePlan(STORE_ID, "PRO", BillingCycle.YEARLY)).willReturn(sample());

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan_code").value("PRO"))
                .andExpect(jsonPath("$.billing_cycle").value("YEARLY"))
                .andExpect(jsonPath("$.amount_due").value(4990.00))
                .andExpect(jsonPath("$.next_billing_date").value("2027-08-10"))
                .andExpect(jsonPath("$.max_stations").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_blankPlanCode_returns400_andServiceNotCalled() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan_code":"","billing_cycle":"MONTHLY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.planCode").exists());

        verify(subscriptionService, never()).changePlan(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_missingBillingCycle_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan_code":"PRO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.billingCycle").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_unknownPlanCode_returns404() throws Exception {
        given(subscriptionService.changePlan(eq(STORE_ID), any(), any()))
                .willThrow(NotFoundException.plan("ENTERPRISE"));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("plan_not_found"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_downgradeBelowStationCount_returns409() throws Exception {
        given(subscriptionService.changePlan(eq(STORE_ID), any(), any()))
                .willThrow(InvalidResourceStateException.stationLimitExceeded(1));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("station_limit_exceeded"));
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
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }
}
