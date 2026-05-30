package com.amx.grocerystore.subscriptions.application;

import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.infrastructure.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionBillingScheduler {

    private static final int GRACE_DAYS = 5;

    private final SubscriptionRepository subscriptionRepository;
    private final StoreService storeService;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void suspendOverdueSubscriptions() {
        LocalDate threshold = LocalDate.now().minusDays(GRACE_DAYS);
        List<Subscription> overdue = subscriptionRepository
                .findByStatusAndNextBillingDateLessThan(SubscriptionStatus.ACTIVE, threshold);

        log.info("Subscription billing sweep: {} ACTIVE subscription(s) past the {}-day grace period",
                overdue.size(), GRACE_DAYS);

        for (Subscription subscription : overdue) {
            subscription.setStatus(SubscriptionStatus.OVERDUE);
            storeService.suspend(subscription.getStoreId());
            log.warn("Auto-suspend store {} - subscription {} overdue (nextBillingDate={})",
                    subscription.getStoreId(), subscription.getId(), subscription.getNextBillingDate());
        }
    }
}
