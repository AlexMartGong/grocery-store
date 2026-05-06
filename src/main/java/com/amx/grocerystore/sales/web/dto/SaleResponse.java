package com.amx.grocerystore.sales.web.dto;

import com.amx.grocerystore.sales.domain.Sale;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.domain.SaleStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SaleResponse(

        UUID id,

        @JsonProperty("store_id")
        UUID storeId,

        @JsonProperty("cashier_id")
        UUID cashierId,

        @JsonProperty("customer_id")
        UUID customerId,

        @JsonProperty("payment_method")
        SalePaymentMethod paymentMethod,

        @JsonProperty("total_amount")
        BigDecimal totalAmount,

        @JsonProperty("amount_paid")
        BigDecimal amountPaid,

        @JsonProperty("amount_credited")
        BigDecimal amountCredited,

        SaleStatus status,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        List<SaleItemResponse> items
) {
    public static SaleResponse from(Sale sale) {
        List<SaleItemResponse> items = sale.getItems() == null
                ? List.of()
                : sale.getItems().stream().map(SaleItemResponse::from).toList();
        return new SaleResponse(
                sale.getId(),
                sale.getStoreId(),
                sale.getUserId(),
                sale.getCustomerId(),
                sale.getPaymentMethod(),
                sale.getTotalAmount(),
                sale.getAmountPaid(),
                sale.getAmountCredited(),
                sale.getStatus(),
                sale.getCreatedAt(),
                items
        );
    }
}
