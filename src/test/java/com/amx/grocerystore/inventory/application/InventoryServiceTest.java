package com.amx.grocerystore.inventory.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.inventory.domain.InventoryMovement;
import com.amx.grocerystore.inventory.domain.InventoryReason;
import com.amx.grocerystore.inventory.domain.events.InventoryLossEvent;
import com.amx.grocerystore.inventory.infrastructure.InventoryMovementRepository;
import com.amx.grocerystore.inventory.web.dto.AdjustStockRequest;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final UUID STORE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void adjustStock_restock_appliesRelativeDeltaWithoutRewritingEntity() {
        Product product = product(new BigDecimal("10.00"));
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(product));

        adjust(new AdjustStockRequest(PRODUCT_ID, new BigDecimal("5"), InventoryReason.RESTOCK, "Supplier delivery"));

        verify(productRepository).addToCurrentStock(PRODUCT_ID, new BigDecimal("5"));
        // The stale snapshot must never be written back: that is the lost update this guards against.
        verify(productRepository, never()).save(any());
        assertThat(product.getCurrentStock()).isEqualByComparingTo("10.00");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        InventoryMovement movement = captor.getValue();
        assertThat(movement.getStoreId()).isEqualTo(STORE_ID);
        assertThat(movement.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(movement.getQuantity()).isEqualByComparingTo("5");
        assertThat(movement.getReason()).isEqualTo(InventoryReason.RESTOCK);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void adjustStock_loss_appliesNegativeDeltaAndPublishesEvent() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.of(product(new BigDecimal("10.00"))));

        adjust(new AdjustStockRequest(PRODUCT_ID, new BigDecimal("-2"), InventoryReason.LOSS, "Broken"));

        verify(productRepository).addToCurrentStock(PRODUCT_ID, new BigDecimal("-2"));
        verify(eventPublisher).publishEvent(
                new InventoryLossEvent(STORE_ID, "Coca-Cola 600ml", new BigDecimal("-2"), "Broken"));
    }

    @Test
    void adjustStock_triggerOwnedReason_isRejected() {
        assertThatThrownBy(() -> adjust(
                new AdjustStockRequest(PRODUCT_ID, new BigDecimal("1"), InventoryReason.SALE, null)))
                .isInstanceOf(InvalidResourceStateException.class)
                .hasFieldOrPropertyWithValue("code", "reason_not_allowed");

        verifyNoInteractions(productRepository, inventoryMovementRepository, eventPublisher);
    }

    @Test
    void adjustStock_unknownProduct_throwsNotFound() {
        given(productRepository.findByIdAndStoreIdAndActiveTrue(PRODUCT_ID, STORE_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adjust(
                new AdjustStockRequest(PRODUCT_ID, new BigDecimal("1"), InventoryReason.RESTOCK, null)))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "product_not_found");

        verify(productRepository, never()).addToCurrentStock(any(), any());
        verifyNoInteractions(inventoryMovementRepository);
    }

    private void adjust(AdjustStockRequest request) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentStoreId).thenReturn(STORE_ID);
            inventoryService.adjustStock(request);
        }
    }

    private Product product(BigDecimal stock) {
        return Product.builder()
                .id(PRODUCT_ID)
                .storeId(STORE_ID)
                .name("Coca-Cola 600ml")
                .salePrice(new BigDecimal("18.00"))
                .currentStock(stock)
                .minimumStock(new BigDecimal("1.00"))
                .unitOfMeasure(UnitOfMeasure.PIECE)
                .active(true)
                .build();
    }
}
