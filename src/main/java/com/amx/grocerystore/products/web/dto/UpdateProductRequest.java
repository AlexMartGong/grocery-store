package com.amx.grocerystore.products.web.dto;

import com.amx.grocerystore.products.domain.UnitOfMeasure;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code PUT /api/v1/products/{id}}. Deliberately has no {@code current_stock}: stock is
 * trigger-owned and changes concurrently, so an edit form loaded before later sales would overwrite
 * them. Stock changes go through {@code POST /api/v1/inventory/movements} (which also writes the
 * ledger row). A client still sending {@code current_stock} is not rejected — the field is ignored.
 */
public record UpdateProductRequest(
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
        @JsonProperty("minimum_stock")
        BigDecimal minimumStock,

        @NotNull
        @JsonProperty("unit_of_measure")
        UnitOfMeasure unitOfMeasure
) {
}
