package com.amx.grocerystore.customers.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {
}
