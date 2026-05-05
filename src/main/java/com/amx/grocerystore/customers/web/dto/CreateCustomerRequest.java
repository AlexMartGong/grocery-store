package com.amx.grocerystore.customers.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCustomerRequest(
        @NotBlank
        @Size(max = 150)
        @JsonProperty("full_name")
        String fullName,

        @Size(max = 20)
        String phone,

        @NotNull
        @PositiveOrZero
        @JsonProperty("credit_limit")
        BigDecimal creditLimit
) {
}
