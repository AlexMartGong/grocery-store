package com.amx.grocerystore.sales.web.dto;

import com.amx.grocerystore.sales.domain.SaleItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record SaleItemResponse(

        UUID id,

        @JsonProperty("product_id")
        UUID productId,

        BigDecimal quantity,

        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        BigDecimal subtotal
) {
    public static SaleItemResponse from(SaleItem item) {
        BigDecimal subtotal = item.getQuantity()
                .multiply(item.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        return new SaleItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal
        );
    }
}
