package com.amx.grocerystore.sales.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.sales.domain.Sale;
import com.amx.grocerystore.sales.domain.SaleItem;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.domain.SaleStatus;
import com.amx.grocerystore.sales.infrastructure.SaleRepository;
import com.amx.grocerystore.sales.web.dto.CreateSaleRequest;
import com.amx.grocerystore.sales.web.dto.SaleItemRequest;
import com.amx.grocerystore.sales.web.dto.SaleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    private static final UUID STORE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASHIER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PRODUCT_ID_2 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CUSTOMER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SaleService saleService;

    @Test
    void createSale_CashMethod_Success() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID_2, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID_2)));
        given(saleRepository.save(any(Sale.class))).willAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.CASH,
                null,
                null,
                List.of(
                        new SaleItemRequest(PRODUCT_ID, new BigDecimal("2"), new BigDecimal("25.50")),
                        new SaleItemRequest(PRODUCT_ID_2, new BigDecimal("1"), new BigDecimal("10.00"))
                )
        );

        SaleResponse response;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            response = saleService.createSale(request);
        }

        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).save(captor.capture());
        Sale saved = captor.getValue();

        assertThat(saved.getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getUserId()).isEqualTo(CASHIER_ID);
        assertThat(saved.getCustomerId()).isNull();
        assertThat(saved.getPaymentMethod()).isEqualTo(SalePaymentMethod.CASH);
        assertThat(saved.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("61.00");
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("61.00");
        assertThat(saved.getAmountCredited()).isEqualByComparingTo("0.00");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getSale()).isSameAs(saved));
        assertThat(response.totalAmount()).isEqualByComparingTo("61.00");
        assertThat(response.amountPaid()).isEqualByComparingTo("61.00");
        assertThat(response.amountCredited()).isEqualByComparingTo("0.00");
    }

    @Test
    void createSale_CreditMethod_Success() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));
        given(customerRepository.findByIdAndStoreId(CUSTOMER_ID, STORE_ID))
                .willReturn(Optional.of(customer(new BigDecimal("100.00"), new BigDecimal("500.00"))));
        given(saleRepository.save(any(Sale.class))).willAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.CREDIT,
                CUSTOMER_ID,
                null,
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("3"), new BigDecimal("20.00")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            saleService.createSale(request);
        }

        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).save(captor.capture());
        Sale saved = captor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getPaymentMethod()).isEqualTo(SalePaymentMethod.CREDIT);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(saved.getAmountCredited()).isEqualByComparingTo("60.00");
        assertThat(saved.getItems()).hasSize(1);
        SaleItem item = saved.getItems().getFirst();
        assertThat(item.getSale()).isSameAs(saved);
        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getQuantity()).isEqualByComparingTo("3");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void createSale_CreditMethod_ExceedsCreditLimit_ThrowsException() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));
        given(customerRepository.findByIdAndStoreId(CUSTOMER_ID, STORE_ID))
                .willReturn(Optional.of(customer(new BigDecimal("450.00"), new BigDecimal("500.00"))));

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.CREDIT,
                CUSTOMER_ID,
                null,
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("2"), new BigDecimal("30.55")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(InvalidResourceStateException.class)
                    .hasFieldOrPropertyWithValue("code", "credit_limit_exceeded");
        }

        verify(saleRepository, never()).save(any());
    }

    @Test
    void createSale_CreditMethod_WithoutCustomer_ThrowsException() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.CREDIT,
                null,
                null,
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("1"), new BigDecimal("10.00")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(InvalidResourceStateException.class)
                    .hasFieldOrPropertyWithValue("code", "customer_required");
        }

        verify(customerRepository, never()).findByIdAndStoreId(any(), any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void createSale_WithInactiveProduct_ThrowsException() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.empty());

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.CASH,
                null,
                null,
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("1"), new BigDecimal("10.00")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code", "product_not_found");
        }

        verify(customerRepository, never()).findByIdAndStoreId(any(), any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void createSale_MixedPayment_Success() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));
        given(customerRepository.findByIdAndStoreId(CUSTOMER_ID, STORE_ID))
                .willReturn(Optional.of(customer(new BigDecimal("0.00"), new BigDecimal("500.00"))));
        given(saleRepository.save(any(Sale.class))).willAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.MIXED,
                CUSTOMER_ID,
                new BigDecimal("150.00"),
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("1"), new BigDecimal("159.50")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            saleService.createSale(request);
        }

        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).save(captor.capture());
        Sale saved = captor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getPaymentMethod()).isEqualTo(SalePaymentMethod.MIXED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("159.50");
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("150.00");
        assertThat(saved.getAmountCredited()).isEqualByComparingTo("9.50");
        assertThat(saved.getItems()).hasSize(1);
    }

    @Test
    void createSale_MixedPayment_AmountPaidGteTotal_ThrowsException() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(activeProduct(PRODUCT_ID)));

        CreateSaleRequest request = new CreateSaleRequest(
                SalePaymentMethod.MIXED,
                CUSTOMER_ID,
                new BigDecimal("100.00"),
                List.of(new SaleItemRequest(PRODUCT_ID, new BigDecimal("1"), new BigDecimal("100.00")))
        );

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            mocked.when(SecurityUtils::currentUserId).thenReturn(CASHIER_ID);
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(InvalidResourceStateException.class)
                    .hasFieldOrPropertyWithValue("code", "invalid_mixed_payment");
        }

        verify(customerRepository, never()).findByIdAndStoreId(any(), any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void list_returnsPagedResponseScopedToStore() {
        Pageable pageable = PageRequest.of(0, 20);
        Sale sale = Sale.builder()
                .id(UUID.randomUUID())
                .storeId(STORE_ID)
                .userId(CASHIER_ID)
                .totalAmount(new BigDecimal("50.00"))
                .amountPaid(new BigDecimal("50.00"))
                .amountCredited(new BigDecimal("0.00"))
                .paymentMethod(SalePaymentMethod.CASH)
                .status(SaleStatus.COMPLETED)
                .items(List.of())
                .build();
        Page<Sale> page = new PageImpl<>(List.of(sale), pageable, 1);
        given(saleRepository.findSales(eq(STORE_ID), isNull(), isNull(), eq(""), isNull(), isNull(), eq(pageable)))
                .willReturn(page);

        PageResponse<SaleResponse> result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            result = saleService.list(null, null, null, null, null, pageable);
        }

        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
        verify(saleRepository).findSales(eq(STORE_ID), isNull(), isNull(), eq(""), isNull(), isNull(), eq(pageable));
    }

    private Product activeProduct(UUID id) {
        return Product.builder()
                .id(id)
                .storeId(STORE_ID)
                .name("Producto " + id)
                .salePrice(new BigDecimal("10.00"))
                .currentStock(new BigDecimal("100.00"))
                .minimumStock(new BigDecimal("0.00"))
                .unitOfMeasure(UnitOfMeasure.PIECE)
                .active(true)
                .build();
    }

    private Customer customer(BigDecimal balance, BigDecimal limit) {
        return Customer.builder()
                .id(CUSTOMER_ID)
                .storeId(STORE_ID)
                .fullName("Cliente Demo")
                .outstandingBalance(balance)
                .creditLimit(limit)
                .active(true)
                .build();
    }
}
