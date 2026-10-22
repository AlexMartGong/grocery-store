package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.PaymentMethod;
import com.amx.grocerystore.subscriptions.domain.SaasPayment;
import com.amx.grocerystore.subscriptions.domain.SaasPaymentConcept;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaasPaymentResponse(
        UUID id,
        UUID storeId,
        UUID subscriptionId,
        BigDecimal amount,
        SaasPaymentConcept concept,
        PaymentMethod paymentMethod,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate nextBillingDate,
        SubscriptionStatus subscriptionStatus) {

    public static SaasPaymentResponse from(SaasPayment payment, Subscription subscription) {
        return new SaasPaymentResponse(
                payment.getId(),
                payment.getStoreId(),
                payment.getSubscriptionId(),
                payment.getAmount(),
                payment.getConcept(),
                payment.getPaymentMethod(),
                payment.getPeriodStart(),
                payment.getPeriodEnd(),
                subscription.getNextBillingDate(),
                subscription.getStatus());
    }
}
