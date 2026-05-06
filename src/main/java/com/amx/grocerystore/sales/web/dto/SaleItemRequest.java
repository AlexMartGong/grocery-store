package com.amx.grocerystore.sales.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(

        @NotNull
        @JsonProperty("product_id")
        UUID productId,

        @NotNull
        @Positive
        BigDecimal quantity,

        @NotNull
        @PositiveOrZero
        @JsonProperty("unit_price")
        BigDecimal unitPrice
) {
}
