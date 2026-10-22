package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionPlan;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A store's subscription plus the entitlements the PWA gates features on.
 * {@code storeId} is deliberately omitted — the caller is either scoped to the
 * store by its JWT or already named it in the path.
 */
public record SubscriptionResponse(
        UUID id,
        @JsonProperty("plan_code") String planCode,
        @JsonProperty("plan_name") String planName,
        @JsonProperty("billing_cycle") BillingCycle billingCycle,
        @JsonProperty("amount_due") BigDecimal amountDue,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("next_billing_date") LocalDate nextBillingDate,
        SubscriptionStatus status,
        @JsonProperty("max_stations") int maxStations,
        @JsonProperty("includes_notifications") boolean includesNotifications,
        @JsonProperty("includes_invoicing") boolean includesInvoicing) {

    /**
     * Must be called inside the transaction that loaded {@code subscription} —
     * it dereferences the LAZY plan association.
     */
    public static SubscriptionResponse from(Subscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();
        return new SubscriptionResponse(
                subscription.getId(),
                plan.getCode(),
                plan.getName(),
                subscription.getBillingCycle(),
                subscription.getBillingCycle().feeFor(plan.getMonthlyFee()),
                subscription.getStartDate(),
                subscription.getNextBillingDate(),
                subscription.getStatus(),
                plan.getMaxStations(),
                plan.isIncludesNotifications(),
                plan.isIncludesInvoicing());
    }
}
