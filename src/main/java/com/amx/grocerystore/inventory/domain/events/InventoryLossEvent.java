package com.amx.grocerystore.inventory.domain.events;

import java.math.BigDecimal;

public record InventoryLossEvent(String productName, BigDecimal quantity, String notes) {
}
