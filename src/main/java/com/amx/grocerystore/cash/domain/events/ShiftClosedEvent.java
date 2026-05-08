package com.amx.grocerystore.cash.domain.events;

import com.amx.grocerystore.cash.web.dto.CashShiftResponse;

public record ShiftClosedEvent(CashShiftResponse shift) {
}
