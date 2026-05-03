package com.amx.grocerystore.products.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateProductPriceRequest(
        @NotNull @PositiveOrZero @JsonProperty("sale_price") BigDecimal salePrice
) {}
