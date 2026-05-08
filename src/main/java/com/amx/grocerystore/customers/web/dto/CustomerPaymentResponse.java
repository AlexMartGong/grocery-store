package com.amx.grocerystore.customers.web.dto;

import com.amx.grocerystore.customers.domain.CustomerPayment;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerPaymentResponse(
        UUID id,
        BigDecimal amount,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {

    public static CustomerPaymentResponse from(CustomerPayment payment) {
        return new CustomerPaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCustomer().getId(),
                payment.getCreatedAt()
        );
    }
}
