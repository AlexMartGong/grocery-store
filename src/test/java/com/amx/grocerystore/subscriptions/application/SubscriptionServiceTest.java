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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final UUID STORE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate NEXT_BILLING = LocalDate.of(2026, 5, 1);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SaasPaymentRepository saasPaymentRepository;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void registerMonthlyPayment_advancesOneMonth_persistsPayment_setsActive_andActivatesStore() {
        Subscription subscription = subscription();
        given(subscriptionRepository.findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                eq(STORE_ID), anyCollection()))
                .willReturn(Optional.of(subscription));
        given(saasPaymentRepository.save(any(SaasPayment.class))).willAnswer(inv -> {
            SaasPayment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        SaasPaymentResponse response = subscriptionService.registerMonthlyPayment(
                STORE_ID, new BigDecimal("499.00"), PaymentMethod.TRANSFER, "Mayo 2026");

        // Billing date rolled forward exactly one month.
        assertThat(subscription.getNextBillingDate()).isEqualTo(NEXT_BILLING.plusMonths(1));
        // Subscription reactivated.
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        // SaasPayment persisted with MONTHLY_FEE + windowed period.
        ArgumentCaptor<SaasPayment> captor = ArgumentCaptor.forClass(SaasPayment.class);
        verify(saasPaymentRepository).save(captor.capture());
        SaasPayment saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getAmount()).isEqualByComparingTo("499.00");
        assertThat(saved.getConcept()).isEqualTo(SaasPaymentConcept.MONTHLY_FEE);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(saved.getPeriodStart()).isEqualTo(NEXT_BILLING);
        assertThat(saved.getPeriodEnd()).isEqualTo(NEXT_BILLING.plusMonths(1));
        assertThat(saved.getNotes()).isEqualTo("Mayo 2026");

        // Cross-module: store block lifted.
        verify(storeService).activate(STORE_ID);

        // Response mirrors persisted + rolled state.
        assertThat(response.amount()).isEqualByComparingTo("499.00");
        assertThat(response.concept()).isEqualTo(SaasPaymentConcept.MONTHLY_FEE);
        assertThat(response.nextBillingDate()).isEqualTo(NEXT_BILLING.plusMonths(1));
        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void registerMonthlyPayment_noSubscription_throwsNotFound_andTouchesNothing() {
        given(subscriptionRepository.findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                eq(STORE_ID), anyCollection()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.registerMonthlyPayment(
                STORE_ID, new BigDecimal("499.00"), PaymentMethod.CASH, null))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "subscription_not_found");

        verify(saasPaymentRepository, never()).save(any());
        verify(storeService, never()).activate(any());
    }

    private Subscription subscription() {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .storeId(STORE_ID)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(SubscriptionServiceTest.NEXT_BILLING)
                .status(SubscriptionStatus.OVERDUE)
                .build();
    }
}
