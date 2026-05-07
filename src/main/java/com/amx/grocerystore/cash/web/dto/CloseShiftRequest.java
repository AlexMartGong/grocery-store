package com.amx.grocerystore.cash.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CloseShiftRequest(
        @JsonProperty("real_balance")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 8, fraction = 2)
        BigDecimal realBalance,

        String notes
) {
}
