package com.amx.grocerystore.cash.domain.events;

import com.amx.grocerystore.cash.web.dto.CashShiftResponse;

import java.util.UUID;

public record ShiftClosedEvent(UUID storeId, CashShiftResponse shift) {
}
