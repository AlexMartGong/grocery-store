package com.amx.grocerystore.inventory.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.inventory.domain.InventoryMovement;
import com.amx.grocerystore.inventory.domain.InventoryReason;
import com.amx.grocerystore.inventory.infrastructure.InventoryMovementRepository;
import com.amx.grocerystore.inventory.web.dto.AdjustStockRequest;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductRepository productRepository;

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
    }
}
