package com.amx.grocerystore.inventory.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.inventory.domain.InventoryMovement;
import com.amx.grocerystore.inventory.domain.InventoryReason;
import com.amx.grocerystore.inventory.domain.events.InventoryLossEvent;
import com.amx.grocerystore.inventory.infrastructure.InventoryMovementRepository;
import com.amx.grocerystore.inventory.web.dto.AdjustStockRequest;
import com.amx.grocerystore.inventory.web.dto.InventoryMovementResponse;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void adjustStock(AdjustStockRequest request) {
        if (request.reason() == InventoryReason.SALE
                || request.reason() == InventoryReason.CANCELLATION) {
            throw InvalidResourceStateException.reasonNotAllowed(request.reason());
        }
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreIdAndActiveTrue(request.productId(), storeId)
                .orElseThrow(() -> NotFoundException.product(request.productId()));

        InventoryMovement movement = InventoryMovement.builder()
                .storeId(storeId)
                .productId(product.getId())
                .quantity(request.quantity())
                .reason(request.reason())
                .notes(request.notes())
                .build();
        inventoryMovementRepository.save(movement);

        product.setCurrentStock(product.getCurrentStock().add(request.quantity()));
        productRepository.save(product);

        if (request.reason() == InventoryReason.LOSS) {
            eventPublisher.publishEvent(
                    new InventoryLossEvent(storeId, product.getName(), request.quantity(), request.notes()));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getProductMovements(UUID productId, Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<InventoryMovement> page =
                inventoryMovementRepository.findByStoreIdAndProductIdOrderByCreatedAtDesc(storeId, productId, pageable);
        return PageResponse.from(page.map(InventoryMovementResponse::from));
    }
}
