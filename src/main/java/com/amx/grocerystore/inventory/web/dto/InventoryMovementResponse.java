package com.amx.grocerystore.inventory.web.dto;

import com.amx.grocerystore.inventory.domain.InventoryMovement;
import com.amx.grocerystore.inventory.domain.InventoryReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID productId,
        BigDecimal quantity,
        InventoryReason reason,
        String notes,
        OffsetDateTime createdAt
) {
    public static InventoryMovementResponse from(InventoryMovement m) {
        return new InventoryMovementResponse(
                m.getId(),
                m.getProductId(),
                m.getQuantity(),
                m.getReason(),
                m.getNotes(),
                m.getCreatedAt());
    }
}
