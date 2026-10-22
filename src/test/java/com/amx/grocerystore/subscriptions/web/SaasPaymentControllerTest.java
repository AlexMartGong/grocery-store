package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.domain.PaymentMethod;
import com.amx.grocerystore.subscriptions.domain.SaasPaymentConcept;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.web.dto.SaasPaymentResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SaasPaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SaasPaymentControllerTest {

    private static final UUID STORE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String URL = "/api/v1/admin/stores/" + STORE_ID + "/payments";
    private static final String VALID_BODY = """
            {"amount":499.00,"method":"TRANSFER","notes":"Mayo 2026"}
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

    private SaasPaymentResponse sample() {
        return new SaasPaymentResponse(
                UUID.randomUUID(), STORE_ID, SUBSCRIPTION_ID, new BigDecimal("499.00"),
                SaasPaymentConcept.MONTHLY_FEE, PaymentMethod.TRANSFER,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1), SubscriptionStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_asSuperAdmin_validBody_returns201() throws Exception {
        given(subscriptionService.registerMonthlyPayment(
                eq(STORE_ID), any(BigDecimal.class), eq(PaymentMethod.TRANSFER), eq("Mayo 2026")))
                .willReturn(sample());

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
                .andExpect(jsonPath("$.subscriptionId").value(SUBSCRIPTION_ID.toString()))
                .andExpect(jsonPath("$.concept").value("MONTHLY_FEE"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextBillingDate").value("2026-06-01"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_negativeAmount_returns400_andServiceNotCalled() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-5.00,"method":"CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.amount").exists());

        verify(subscriptionService, never()).registerMonthlyPayment(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_missingMethod_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":499.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.method").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_subscriptionNotFound_returns404() throws Exception {
        given(subscriptionService.registerMonthlyPayment(eq(STORE_ID), any(), any(), any()))
                .willThrow(NotFoundException.subscription(STORE_ID));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("subscription_not_found"));
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
