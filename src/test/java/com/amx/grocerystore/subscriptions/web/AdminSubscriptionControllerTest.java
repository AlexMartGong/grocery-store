package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionSummaryResponse;
import com.amx.grocerystore.subscriptions.web.dto.UpcomingBillingResponse;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSubscriptionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminSubscriptionControllerTest {

    private static final UUID STORE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String UPCOMING_URL = "/api/v1/admin/subscriptions/upcoming-billings";
    private static final String SUMMARY_URL = "/api/v1/admin/subscriptions/summary";

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

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void upcomingBillings_asSuperAdmin_returnsSnakeCaseRows() throws Exception {
        given(subscriptionService.listUpcomingBillings()).willReturn(List.of(
                new UpcomingBillingResponse(STORE_ID, "Abarrotes La Pasadita", "5512345678",
                        LocalDate.of(2026, 8, 14), BillingCycle.YEARLY, new BigDecimal("4990.00"))));

        mvc.perform(get(UPCOMING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].store_id").value(STORE_ID.toString()))
                .andExpect(jsonPath("$[0].store_name").value("Abarrotes La Pasadita"))
                .andExpect(jsonPath("$[0].whatsapp").value("5512345678"))
                .andExpect(jsonPath("$[0].next_billing_date").value("2026-08-14"))
                .andExpect(jsonPath("$[0].billing_cycle").value("YEARLY"))
                .andExpect(jsonPath("$[0].amount_due").value(4990.00));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void upcomingBillings_storeWithoutWhatsapp_serializesNull() throws Exception {
        given(subscriptionService.listUpcomingBillings()).willReturn(List.of(
                new UpcomingBillingResponse(STORE_ID, "Sin WhatsApp", null,
                        LocalDate.of(2026, 8, 12), BillingCycle.MONTHLY, new BigDecimal("299.00"))));

        mvc.perform(get(UPCOMING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].whatsapp").isEmpty())
                .andExpect(jsonPath("$[0].amount_due").value(299.00));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void summary_asSuperAdmin_returnsKpis() throws Exception {
        Map<String, Long> tiers = new LinkedHashMap<>();
        tiers.put("STANDARD", 0L);
        tiers.put("PRO", 3L);
        tiers.put("BUSINESS", 1L);

        given(subscriptionService.summary()).willReturn(new SubscriptionSummaryResponse(
                4L, 2L, new BigDecimal("5289.00"), tiers));

        mvc.perform(get(SUMMARY_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active_subscriptions").value(4))
                .andExpect(jsonPath("$.due_next_7_days").value(2))
                .andExpect(jsonPath("$.due_next_7_days_amount").value(5289.00))
                .andExpect(jsonPath("$.tier_distribution.STANDARD").value(0))
                .andExpect(jsonPath("$.tier_distribution.PRO").value(3))
                .andExpect(jsonPath("$.tier_distribution.BUSINESS").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void upcomingBillings_asOwner_returns403() throws Exception {
        mvc.perform(get(UPCOMING_URL)).andExpect(status().isForbidden());

        verify(subscriptionService, never()).listUpcomingBillings();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void summary_asOwner_returns403() throws Exception {
        mvc.perform(get(SUMMARY_URL)).andExpect(status().isForbidden());

        verify(subscriptionService, never()).summary();
    }

    @Test
    void summary_unauthenticated_returns401() throws Exception {
        mvc.perform(get(SUMMARY_URL)).andExpect(status().isUnauthorized());
    }
}
