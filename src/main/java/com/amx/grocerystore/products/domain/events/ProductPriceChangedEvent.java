package com.amx.grocerystore.products.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPriceChangedEvent(
        UUID storeId,
        UUID userId,
        UUID productId,
        BigDecimal oldPrice,
        BigDecimal newPrice
) {
}
