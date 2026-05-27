package com.amx.grocerystore.inventory.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockEvent(UUID storeId, String productName, BigDecimal remainingStock, BigDecimal minStock) {
}
