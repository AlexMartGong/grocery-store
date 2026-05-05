package com.amx.grocerystore.customers.web.dto;

import com.amx.grocerystore.customers.domain.Customer;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        @JsonProperty("full_name") String fullName,
        String phone,
        @JsonProperty("outstanding_balance") BigDecimal outstandingBalance,
        @JsonProperty("credit_limit") BigDecimal creditLimit,
        boolean active,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getOutstandingBalance(),
                customer.getCreditLimit(),
                customer.isActive(),
                customer.getCreatedAt()
        );
    }
}
