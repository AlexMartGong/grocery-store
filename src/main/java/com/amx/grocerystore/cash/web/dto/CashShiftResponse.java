package com.amx.grocerystore.cash.web.dto;

import com.amx.grocerystore.cash.domain.CashRegisterShift;
import com.amx.grocerystore.cash.domain.ShiftStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashShiftResponse(
        UUID id,
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("user_id") UUID userId,
        ShiftStatus status,
        @JsonProperty("opened_at") OffsetDateTime openedAt,
        @JsonProperty("closed_at") OffsetDateTime closedAt,
        @JsonProperty("opening_balance") BigDecimal openingBalance,
        @JsonProperty("expected_balance") BigDecimal expectedBalance,
        @JsonProperty("real_balance") BigDecimal realBalance,
        BigDecimal difference,
        String notes
) {
    public static CashShiftResponse from(CashRegisterShift s) {
        return new CashShiftResponse(
                s.getId(),
                s.getStoreId(),
                s.getUserId(),
                s.getStatus(),
                s.getOpenedAt(),
                s.getClosedAt(),
                s.getOpeningBalance(),
                s.getExpectedBalance(),
                s.getRealBalance(),
                s.getDifference(),
                s.getNotes()
        );
    }
}
