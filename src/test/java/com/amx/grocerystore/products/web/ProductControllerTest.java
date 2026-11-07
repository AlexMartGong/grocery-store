package com.amx.grocerystore.products.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.products.application.ProductService;
import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.amx.grocerystore.products.web.dto.ProductResponse;
import com.amx.grocerystore.products.web.dto.UpdateProductRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    private static final String URL = "/api/v1/products/";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

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
    @WithMockUser(roles = "OWNER")
    void put_legacyBodyWithCurrentStock_isAcceptedAndStockIgnored() throws Exception {
        UUID id = UUID.randomUUID();
        given(productService.update(eq(id), any())).willReturn(response(id, new BigDecimal("7.00")));

        // Current PWA still sends current_stock on edit: it must not be rejected, and it must not
        // reach the service (the response shows the DB stock, not the stale form value).
        mvc.perform(put(URL + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"7501055300075",
                                  "name":"Coca-Cola 600ml",
                                  "purchase_cost":12.00,
                                  "sale_price":18.00,
                                  "current_stock":10,
                                  "minimum_stock":5,
                                  "unit_of_measure":"PIECE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_stock").value(7.00));

        ArgumentCaptor<UpdateProductRequest> captor = ArgumentCaptor.forClass(UpdateProductRequest.class);
        verify(productService).update(eq(id), captor.capture());
        assertThat(captor.getValue().salePrice()).isEqualByComparingTo("18.00");
        assertThat(captor.getValue().minimumStock()).isEqualByComparingTo("5");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_withoutCurrentStock_isValid() throws Exception {
        UUID id = UUID.randomUUID();
        given(productService.update(eq(id), any())).willReturn(response(id, new BigDecimal("7.00")));

        mvc.perform(put(URL + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Coca-Cola 600ml","sale_price":18.00,"minimum_stock":5,"unit_of_measure":"PIECE"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_missingSalePrice_returns400() throws Exception {
        mvc.perform(put(URL + UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Coca-Cola 600ml","minimum_stock":5,"unit_of_measure":"PIECE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.salePrice").exists());

        verify(productService, never()).update(any(), any());
    }

    private ProductResponse response(UUID id, BigDecimal stock) {
        return new ProductResponse(id, "7501055300075", "Coca-Cola 600ml", new BigDecimal("12.00"),
                new BigDecimal("18.00"), stock, new BigDecimal("5.00"), UnitOfMeasure.PIECE, null, true,
                OffsetDateTime.now());
    }
}
