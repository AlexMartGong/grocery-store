package com.amx.grocerystore.dashboard.web.dto;

import com.amx.grocerystore.dashboard.domain.ProductToRestockView;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductToRestockResponse(
        UUID productId,
        String name,
        BigDecimal currentStock,
        BigDecimal minimumStock
) {

    public static ProductToRestockResponse from(ProductToRestockView view) {
        return new ProductToRestockResponse(
                view.getProductId(),
                view.getName(),
                view.getCurrentStock(),
                view.getMinimumStock());
    }
}
