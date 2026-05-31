package com.amx.grocerystore.dashboard.web.dto;

import com.amx.grocerystore.dashboard.domain.SalesTodayView;

import java.math.BigDecimal;

public record SalesTodayResponse(
        long tickets,
        BigDecimal dayTotal,
        BigDecimal dayCollected,
        BigDecimal dayOnCredit
) {

    public static SalesTodayResponse from(SalesTodayView view) {
        return new SalesTodayResponse(
                view.getTickets() == null ? 0L : view.getTickets(),
                view.getDayTotal(),
                view.getDayCollected(),
                view.getDayOnCredit());
    }

    public static SalesTodayResponse empty() {
        return new SalesTodayResponse(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
