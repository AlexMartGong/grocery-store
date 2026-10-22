package com.amx.grocerystore.subscriptions.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Platform-wide subscription KPIs for the admin overview header.
 * <p>
 * {@code tierDistribution} is keyed by plan {@code code} and always lists <em>every</em>
 * active tier, zero-count ones included, in ascending price order — the UI renders it
 * directly without a second call to the plan catalog.
 */
public record SubscriptionSummaryResponse(
        @JsonProperty("active_subscriptions") long activeSubscriptions,
        @JsonProperty("due_next_7_days") long dueNext7Days,
        @JsonProperty("due_next_7_days_amount") BigDecimal dueNext7DaysAmount,
        @JsonProperty("tier_distribution") Map<String, Long> tierDistribution) {
}
