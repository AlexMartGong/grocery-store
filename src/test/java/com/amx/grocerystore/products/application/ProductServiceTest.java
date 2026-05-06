package com.amx.grocerystore.products.application;

import com.amx.grocerystore.core.security.TenantAuthenticationToken;
import com.amx.grocerystore.core.security.UserPrincipal;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.products.web.dto.ProductRequest;
import com.amx.grocerystore.products.web.dto.ProductResponse;
import com.amx.grocerystore.users.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), TENANT_ID, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new TenantAuthenticationToken(principal));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product product(UUID id, boolean active) {
        return Product.builder()
                .id(id)
                .storeId(ProductServiceTest.TENANT_ID)
                .barcode("7501000000001")
                .name("Leche 1L")
                .purchaseCost(new BigDecimal("15.00"))
                .salePrice(new BigDecimal("20.00"))
                .currentStock(new BigDecimal("10.00"))
                .minimumStock(new BigDecimal("5.00"))
                .unitOfMeasure(UnitOfMeasure.PIECE)
                .active(active)
                .build();
    }

    private ProductRequest request(String barcode) {
        return new ProductRequest(
                barcode,
                "Leche 1L",
                new BigDecimal("15.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                UnitOfMeasure.PIECE
        );
    }

    @Test
    void create_persistsProduct_withStoreIdFromContext_andActiveTrue() {
        ProductRequest req = request("7501000000001");
        given(productRepository.existsByStoreIdAndBarcode(TENANT_ID, "7501000000001")).willReturn(false);
        given(productRepository.save(any(Product.class))).willAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.create(req);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(TENANT_ID);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.PIECE);
        assertThat(response.barcode()).isEqualTo("7501000000001");
    }

    @Test
    void create_duplicateBarcode_throwsAndDoesNotSave() {
        ProductRequest req = request("7501000000001");
        given(productRepository.existsByStoreIdAndBarcode(TENANT_ID, "7501000000001")).willReturn(true);

        assertThatThrownBy(() -> productService.create(req))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("product_barcode_taken");

        verify(productRepository, never()).save(any());
    }

    @Test
    void create_nullBarcode_skipsUniquenessCheck() {
        ProductRequest req = request(null);
        given(productRepository.save(any(Product.class))).willAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        productService.create(req);

        verify(productRepository, never()).existsByStoreIdAndBarcode(any(), any());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void findById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("product_not_found");
    }

    @Test
    void findById_returnsResponse() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(product(id, true)));

        ProductResponse response = productService.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.active()).isTrue();
    }

    @Test
    void list_defaultActiveNull_callsFindProducts_withActiveTrue_excludesInactive() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product(UUID.randomUUID(), true)));
        given(productRepository.findProducts(TENANT_ID, Boolean.TRUE, "", pageable)).willReturn(page);

        PageResponse<ProductResponse> response = productService.list(null, null, pageable);

        verify(productRepository).findProducts(TENANT_ID, Boolean.TRUE, "", pageable);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().active()).isTrue();
    }

    @Test
    void list_activeTrue_callsFindProducts_withActiveTrue() {
        Pageable pageable = PageRequest.of(0, 10);
        given(productRepository.findProducts(TENANT_ID, Boolean.TRUE, "", pageable))
                .willReturn(new PageImpl<>(List.of()));

        productService.list(true, null, pageable);

        verify(productRepository).findProducts(TENANT_ID, Boolean.TRUE, "", pageable);
    }

    @Test
    void list_activeFalse_callsFindProducts_withNullActive_includesAll() {
        Pageable pageable = PageRequest.of(0, 10);
        given(productRepository.findProducts(TENANT_ID, null, "", pageable))
                .willReturn(new PageImpl<>(List.of()));

        productService.list(false, null, pageable);

        verify(productRepository).findProducts(TENANT_ID, null, "", pageable);
    }

    @Test
    void list_withSearch_passesTermVerbatim() {
        Pageable pageable = PageRequest.of(0, 10);
        given(productRepository.findProducts(TENANT_ID, Boolean.TRUE, "leche", pageable))
                .willReturn(new PageImpl<>(List.of()));

        productService.list(null, "leche", pageable);

        verify(productRepository).findProducts(TENANT_ID, Boolean.TRUE, "leche", pageable);
    }

    @Test
    void update_barcodeTakenByAnother_throws() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(product(id, true)));
        given(productRepository.existsByStoreIdAndBarcodeAndIdNot(TENANT_ID, "NEW_CODE", id)).willReturn(true);

        ProductRequest req = request("NEW_CODE");

        assertThatThrownBy(() -> productService.update(id, req))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("product_barcode_taken");

        verify(productRepository, never()).save(any());
    }

    @Test
    void update_happyPath_mutatesFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        Product existing = product(id, true);
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));
        given(productRepository.existsByStoreIdAndBarcodeAndIdNot(TENANT_ID, "7501000000001", id)).willReturn(false);
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        ProductRequest req = new ProductRequest(
                "7501000000001",
                "Leche 1L Nueva",
                new BigDecimal("16.00"),
                new BigDecimal("22.50"),
                new BigDecimal("12.00"),
                new BigDecimal("3.00"),
                UnitOfMeasure.KILO
        );
        ProductResponse response = productService.update(id, req);

        assertThat(existing.getName()).isEqualTo("Leche 1L Nueva");
        assertThat(existing.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.KILO);
        assertThat(existing.getSalePrice()).isEqualByComparingTo("22.50");
        assertThat(existing.isActive()).isTrue();
        assertThat(response.name()).isEqualTo("Leche 1L Nueva");
    }

    @Test
    void delete_active_setsActiveFalse_doesNotRemove() {
        UUID id = UUID.randomUUID();
        Product existing = product(id, true);
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));

        productService.softDelete(id);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(existing.isActive()).isFalse();
        verify(productRepository, never()).delete(any());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void delete_alreadyInactive_isIdempotent_noSave() {
        UUID id = UUID.randomUUID();
        Product existing = product(id, false);
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));

        productService.softDelete(id);

        verify(productRepository, never()).save(any());
        verify(productRepository, never()).delete(any());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void delete_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.softDelete(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("product_not_found");

        verify(productRepository, never()).save(any());
    }

    @Test
    void findByBarcode_active_returnsResponse() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByStoreIdAndBarcodeAndActiveTrue(TENANT_ID, "7501000000001"))
                .willReturn(Optional.of(product(id, true)));

        ProductResponse response = productService.findByBarcode("7501000000001");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.barcode()).isEqualTo("7501000000001");
    }

    @Test
    void findByBarcode_missingOrInactive_throwsNotFound() {
        given(productRepository.findByStoreIdAndBarcodeAndActiveTrue(eq(TENANT_ID), eq("MISS")))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findByBarcode("MISS"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("product_not_found");
    }
}
