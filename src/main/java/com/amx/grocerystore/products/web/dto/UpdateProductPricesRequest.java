package com.amx.grocerystore.products.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateProductPricesRequest(
        @PositiveOrZero @JsonProperty("sale_price") BigDecimal salePrice,
        @PositiveOrZero @JsonProperty("purchase_cost") BigDecimal purchaseCost
) {
}
