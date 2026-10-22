package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One collectable renewal in the admin's 7-day window: who to charge, how to reach them,
 * and how much.
 * <p>
 * {@code amountDue} is the cycle-aware fee ({@code billingCycle.feeFor(plan.monthlyFee)}),
 * not the plan's raw monthly fee — a YEARLY subscriber owes ten months, not one.
 */
public record UpcomingBillingResponse(
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("store_name") String storeName,
        String whatsapp,
        @JsonProperty("next_billing_date") LocalDate nextBillingDate,
        @JsonProperty("billing_cycle") BillingCycle billingCycle,
        @JsonProperty("amount_due") BigDecimal amountDue) {
}
