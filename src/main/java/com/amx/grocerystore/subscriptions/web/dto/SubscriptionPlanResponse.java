package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.domain.SubscriptionPlan;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        @JsonProperty("monthly_fee") BigDecimal monthlyFee,
        @JsonProperty("yearly_fee") BigDecimal yearlyFee,
        @JsonProperty("setup_fee") BigDecimal setupFee,
        @JsonProperty("max_stations") int maxStations,
        @JsonProperty("includes_notifications") boolean includesNotifications,
        @JsonProperty("includes_invoicing") boolean includesInvoicing,
        String description) {

    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getMonthlyFee(),
                BillingCycle.YEARLY.feeFor(plan.getMonthlyFee()),
                plan.getSetupFee(),
                plan.getMaxStations(),
                plan.isIncludesNotifications(),
                plan.isIncludesInvoicing(),
                plan.getDescription());
    }
}
