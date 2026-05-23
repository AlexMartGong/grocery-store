package com.amx.grocerystore.sales.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.sales.application.SaleService;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.domain.SaleStatus;
import com.amx.grocerystore.sales.web.dto.SaleItemResponse;
import com.amx.grocerystore.sales.web.dto.SaleResponse;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SaleControllerTest {

    private static final String URL = "/api/v1/sales";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaleService saleService;

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
    @WithMockUser(roles = "CASHIER")
    void post_cashSale_returns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        given(saleService.createSale(any())).willReturn(sample(
                id, SalePaymentMethod.CASH,
                new BigDecimal("51.00"), new BigDecimal("51.00"), new BigDecimal("0.00"),
                null
        ));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment_method":"CASH",
                                  "items":[{"product_id":"%s","quantity":2,"unit_price":25.50}]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sales/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.payment_method").value("CASH"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void post_creditSale_asOwner_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        given(saleService.createSale(any())).willReturn(sample(
                id, SalePaymentMethod.CREDIT,
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("100.00"),
                customerId
        ));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment_method":"CREDIT",
                                  "customer_id":"%s",
                                  "items":[{"product_id":"%s","quantity":1,"unit_price":100.00}]
                                }
                                """.formatted(customerId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sales/" + id))
                .andExpect(jsonPath("$.payment_method").value("CREDIT"))
                .andExpect(jsonPath("$.customer_id").value(customerId.toString()));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_emptyItems_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payment_method":"CASH","items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.items").exists());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_missingPaymentMethod_returns400() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"product_id":"%s","quantity":1,"unit_price":10.00}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_creditExceedsLimit_returns409() throws Exception {
        given(saleService.createSale(any()))
                .willThrow(InvalidResourceStateException.creditLimitExceeded());

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment_method":"CREDIT",
                                  "customer_id":"%s",
                                  "items":[{"product_id":"%s","quantity":1,"unit_price":100.00}]
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("credit_limit_exceeded"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_inactiveProduct_returns404() throws Exception {
        UUID productId = UUID.randomUUID();
        given(saleService.createSale(any())).willThrow(NotFoundException.product(productId));

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment_method":"CASH",
                                  "items":[{"product_id":"%s","quantity":1,"unit_price":10.00}]
                                }
                                """.formatted(productId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product_not_found"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void post_mixedPayment_invalidSplit_returns409() throws Exception {
        given(saleService.createSale(any()))
                .willThrow(InvalidResourceStateException.invalidMixedPayment());

        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment_method":"MIXED",
                                  "customer_id":"%s",
                                  "amount_paid":100.00,
                                  "items":[{"product_id":"%s","quantity":1,"unit_price":100.00}]
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_mixed_payment"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void get_list_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        SaleResponse sale = sample(
                id, SalePaymentMethod.CASH,
                new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("0.00"),
                null
        );
        given(saleService.list(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(sale), 0, 20, 1L, 1));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    }

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payment_method":"CASH","items":[{"product_id":"%s","quantity":1,"unit_price":10.00}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void post_wrongRole_returns403() throws Exception {
        mvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payment_method":"CASH","items":[{"product_id":"%s","quantity":1,"unit_price":10.00}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private SaleResponse sample(UUID id,
                                SalePaymentMethod method,
                                BigDecimal total,
                                BigDecimal paid,
                                BigDecimal credited,
                                UUID customerId) {
        SaleItemResponse item = new SaleItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1"),
                total,
                total
        );
        return new SaleResponse(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                customerId,
                method,
                total,
                paid,
                credited,
                SaleStatus.COMPLETED,
                OffsetDateTime.now(),
                List.of(item)
        );
    }
}
