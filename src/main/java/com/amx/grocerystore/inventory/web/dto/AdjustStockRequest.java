package com.amx.grocerystore.inventory.web.dto;

import com.amx.grocerystore.inventory.domain.InventoryReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull @JsonProperty("product_id") UUID productId,
        @NotNull BigDecimal quantity,
        @NotNull InventoryReason reason,
        String notes
) {
}
