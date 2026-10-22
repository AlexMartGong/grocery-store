package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.security.TenantAuthenticationToken;
import com.amx.grocerystore.core.security.UserPrincipal;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionResponse;
import com.amx.grocerystore.users.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MySubscriptionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MySubscriptionControllerTest {

    private static final UUID STORE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String URL = "/api/v1/stores/me/subscription";

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

    /**
     * SecurityUtils.currentStoreId() reads a UserPrincipal off the context, so these tests
     * need a real TenantAuthenticationToken — @WithMockUser's principal would not resolve.
     */
    private Authentication tenant(UserRole role) {
        return new TenantAuthenticationToken(
                new UserPrincipal(UUID.randomUUID(), STORE_ID, "user", role));
    }

    private SubscriptionResponse sample() {
        return new SubscriptionResponse(
                UUID.randomUUID(), "PRO", "Pro", BillingCycle.MONTHLY, new BigDecimal("499.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10), SubscriptionStatus.ACTIVE,
                2, true, false);
    }

    @Test
    void get_asOwner_returnsOwnStoreEntitlements() throws Exception {
        given(subscriptionService.getActiveSubscription(STORE_ID)).willReturn(sample());

        mvc.perform(get(URL).with(authentication(tenant(UserRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_code").value("PRO"))
                .andExpect(jsonPath("$.billing_cycle").value("MONTHLY"))
                .andExpect(jsonPath("$.amount_due").value(499.00))
                .andExpect(jsonPath("$.max_stations").value(2))
                .andExpect(jsonPath("$.includes_notifications").value(true))
                .andExpect(jsonPath("$.includes_invoicing").value(false));

        // Tenant comes from the JWT principal, never from the client.
        verify(subscriptionService).getActiveSubscription(STORE_ID);
    }

    @Test
    void get_storeWithoutSubscription_returns404() throws Exception {
        given(subscriptionService.getActiveSubscription(STORE_ID))
                .willThrow(NotFoundException.subscription(STORE_ID));

        mvc.perform(get(URL).with(authentication(tenant(UserRole.OWNER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("subscription_not_found"));
    }

    @Test
    void get_asCashier_returns403() throws Exception {
        mvc.perform(get(URL).with(authentication(tenant(UserRole.CASHIER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
