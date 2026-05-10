package com.amx.grocerystore.sales.web.dto;

import com.amx.grocerystore.sales.domain.CancellationReason;
import jakarta.validation.constraints.NotNull;

public record CancelSaleRequest(
        @NotNull CancellationReason reason,
        String notes
) {
}
