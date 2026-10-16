package com.amx.grocerystore.subscriptions.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How often a subscription is billed. Owns the cycle-dependent rules so the fee
 * formula, the period length and the payment concept cannot drift apart across
 * the service and the response DTOs.
 */
public enum BillingCycle {

    MONTHLY,
    YEARLY;

    /**
     * Amount charged per period. Yearly bills 10 months for 12 — two months free.
     */
    public BigDecimal feeFor(BigDecimal monthlyFee) {
        return switch (this) {
            case MONTHLY -> monthlyFee;
            case YEARLY -> monthlyFee.multiply(BigDecimal.TEN);
        };
    }

    /**
     * End of the period that starts at {@code from} — also the next billing date.
     */
    public LocalDate advance(LocalDate from) {
        return switch (this) {
            case MONTHLY -> from.plusMonths(1);
            case YEARLY -> from.plusYears(1);
        };
    }

    public SaasPaymentConcept paymentConcept() {
        return switch (this) {
            case MONTHLY -> SaasPaymentConcept.MONTHLY_FEE;
            case YEARLY -> SaasPaymentConcept.YEARLY_FEE;
        };
    }
}
