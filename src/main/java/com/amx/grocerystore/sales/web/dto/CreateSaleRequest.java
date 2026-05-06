package com.amx.grocerystore.sales.web.dto;

import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(

        @NotNull
        @JsonProperty("payment_method")
        SalePaymentMethod paymentMethod,

        @JsonProperty("customer_id")
        UUID customerId,

        @PositiveOrZero
        @JsonProperty("amount_paid")
        BigDecimal amountPaid,

        @NotEmpty
        List<@Valid SaleItemRequest> items
) {
}
