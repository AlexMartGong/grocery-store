package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterSaasPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod method,
        @Size(max = 1000) String notes) {
}
