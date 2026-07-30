package com.amx.grocerystore.products.web.dto;

import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String barcode,
        String name,
        @JsonProperty("purchase_cost") BigDecimal purchaseCost,
        @JsonProperty("sale_price") BigDecimal salePrice,
        @JsonProperty("current_stock") BigDecimal currentStock,
        @JsonProperty("minimum_stock") BigDecimal minimumStock,
        @JsonProperty("unit_of_measure") UnitOfMeasure unitOfMeasure,
        @JsonProperty("image_url") String imageUrl,
        boolean active,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getBarcode(),
                product.getName(),
                product.getPurchaseCost(),
                product.getSalePrice(),
                product.getCurrentStock(),
                product.getMinimumStock(),
                product.getUnitOfMeasure(),
                product.getImageUrl(),
                product.isActive(),
                product.getUpdatedAt()
        );
    }
}
