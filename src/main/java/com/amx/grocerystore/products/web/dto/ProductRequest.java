package com.amx.grocerystore.products.web.dto;

import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @Size(max = 50)
        String barcode,

        @NotBlank
        @Size(max = 150)
        String name,

        @DecimalMin("0.00")
        @JsonProperty("purchase_cost")
        BigDecimal purchaseCost,

        @NotNull
        @DecimalMin("0.00")
        @JsonProperty("sale_price")
        BigDecimal salePrice,

        @NotNull
        @DecimalMin("0.00")
        @JsonProperty("current_stock")
        BigDecimal currentStock,

        @NotNull
        @DecimalMin("0.00")
        @JsonProperty("minimum_stock")
        BigDecimal minimumStock,

        @NotNull
        @JsonProperty("unit_of_measure")
        UnitOfMeasure unitOfMeasure
) {
}
