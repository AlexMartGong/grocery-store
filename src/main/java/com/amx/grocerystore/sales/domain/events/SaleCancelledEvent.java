package com.amx.grocerystore.sales.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleCancelledEvent(UUID saleId, BigDecimal totalAmount, String reason) {
}
