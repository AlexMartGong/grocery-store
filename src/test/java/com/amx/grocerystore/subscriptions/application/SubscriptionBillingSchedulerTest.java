package com.amx.grocerystore.subscriptions.application;

import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingSchedulerTest {

    private static final UUID STORE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STORE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private SubscriptionBillingScheduler scheduler;

    @Test
    void sweep_marksOverdueSubscriptions_andSuspendsTheirStores() {
        Subscription a = subscription(STORE_A, LocalDate.of(2026, 4, 1));
        Subscription b = subscription(STORE_B, LocalDate.of(2026, 4, 10));
        given(subscriptionRepository.findByStatusAndNextBillingDateLessThan(
                eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of(a, b));

        scheduler.suspendOverdueSubscriptions();

        // Each past-grace subscription flipped to OVERDUE.
        assertThat(a.getStatus()).isEqualTo(SubscriptionStatus.OVERDUE);
        assertThat(b.getStatus()).isEqualTo(SubscriptionStatus.OVERDUE);

        // Cross-module: associated store suspended for each.
        verify(storeService).suspend(STORE_A);
        verify(storeService).suspend(STORE_B);
    }

    @Test
    void sweep_queriesWithThreshold5DaysBeforeToday_strictlyBefore() {
        given(subscriptionRepository.findByStatusAndNextBillingDateLessThan(
                eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of());

        scheduler.suspendOverdueSubscriptions();

        ArgumentCaptor<LocalDate> threshold = ArgumentCaptor.forClass(LocalDate.class);
        verify(subscriptionRepository)
                .findByStatusAndNextBillingDateLessThan(eq(SubscriptionStatus.ACTIVE), threshold.capture());
        assertThat(threshold.getValue()).isEqualTo(LocalDate.now().minusDays(5));
    }

    @Test
    void sweep_noOverdueSubscriptions_doesNotTouchStores() {
        given(subscriptionRepository.findByStatusAndNextBillingDateLessThan(
                eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of());

        scheduler.suspendOverdueSubscriptions();

        verifyNoInteractions(storeService);
    }

    private Subscription subscription(UUID storeId, LocalDate nextBilling) {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(nextBilling)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }
}
