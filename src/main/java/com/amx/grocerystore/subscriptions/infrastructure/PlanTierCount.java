package com.amx.grocerystore.subscriptions.infrastructure;

/**
 * Projection for "how many ACTIVE subscriptions sit on each tier".
 * <p>
 * Only tiers with at least one subscriber come back — {@code SubscriptionService.summary()}
 * seeds the zeros from the plan catalog so empty tiers stay visible in the UI.
 */
public interface PlanTierCount {

    String getPlanCode();

    long getTotal();
}
