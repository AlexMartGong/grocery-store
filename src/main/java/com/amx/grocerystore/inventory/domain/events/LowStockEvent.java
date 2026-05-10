package com.amx.grocerystore.inventory.domain.events;

import java.math.BigDecimal;

public record LowStockEvent(String productName, BigDecimal remainingStock, BigDecimal minStock) {
}
