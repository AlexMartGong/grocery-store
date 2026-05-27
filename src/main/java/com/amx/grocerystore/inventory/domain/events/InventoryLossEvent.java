package com.amx.grocerystore.inventory.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryLossEvent(UUID storeId, String productName, BigDecimal quantity, String notes) {
}
