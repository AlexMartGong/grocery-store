package com.amx.grocerystore.subscriptions.application;

import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.subscriptions.domain.PaymentMethod;
import com.amx.grocerystore.subscriptions.domain.SaasPayment;
import com.amx.grocerystore.subscriptions.domain.SaasPaymentConcept;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.infrastructure.SaasPaymentRepository;
import com.amx.grocerystore.subscriptions.infrastructure.SubscriptionRepository;
import com.amx.grocerystore.subscriptions.web.dto.SaasPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SaasPaymentRepository saasPaymentRepository;
    private final StoreService storeService;

    @Transactional
    public SaasPaymentResponse registerMonthlyPayment(UUID storeId, BigDecimal amount,
                                                      PaymentMethod method, String notes) {
        Subscription subscription = subscriptionRepository
                .findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                        storeId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.OVERDUE))
                .orElseThrow(() -> NotFoundException.subscription(storeId));

        LocalDate periodStart = subscription.getNextBillingDate();
        LocalDate periodEnd = periodStart.plusMonths(1);

        SaasPayment payment = saasPaymentRepository.save(SaasPayment.builder()
                .storeId(storeId)
                .amount(amount)
                .concept(SaasPaymentConcept.MONTHLY_FEE)
                .paymentMethod(method)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .notes(notes)
                .build());

        subscription.setNextBillingDate(periodEnd);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        storeService.activate(storeId);

        return SaasPaymentResponse.from(payment, subscription);
    }
}
