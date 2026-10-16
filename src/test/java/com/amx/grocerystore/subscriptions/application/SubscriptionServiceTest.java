package com.amx.grocerystore.subscriptions.application;

import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.infrastructure.StoreStationRepository;
import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.amx.grocerystore.subscriptions.domain.PaymentMethod;
import com.amx.grocerystore.subscriptions.domain.SaasPayment;
import com.amx.grocerystore.subscriptions.domain.SaasPaymentConcept;
import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionPlan;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import com.amx.grocerystore.subscriptions.infrastructure.PlanTierCount;
import com.amx.grocerystore.subscriptions.infrastructure.SaasPaymentRepository;
import com.amx.grocerystore.subscriptions.infrastructure.SubscriptionPlanRepository;
import com.amx.grocerystore.subscriptions.infrastructure.SubscriptionRepository;
import com.amx.grocerystore.subscriptions.web.dto.SaasPaymentResponse;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionPlanResponse;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionResponse;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionSummaryResponse;
import com.amx.grocerystore.subscriptions.web.dto.UpcomingBillingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final UUID STORE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate NEXT_BILLING = LocalDate.of(2026, 5, 1);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SaasPaymentRepository saasPaymentRepository;

    @Mock
    private StoreStationRepository storeStationRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    // ------------------------------------------------------------------
    // listPlans
    // ------------------------------------------------------------------

    @Test
    void listPlans_returnsActiveTiersWithYearlyFeeAtTenMonths() {
        given(subscriptionPlanRepository.findByActiveTrueOrderByMonthlyFeeAsc())
                .willReturn(List.of(plan("STANDARD", "299.00", 1, false, false),
                        plan("BUSINESS", "899.00", 5, true, true)));

        List<SubscriptionPlanResponse> plans = subscriptionService.listPlans();

        assertThat(plans).hasSize(2);
        assertThat(plans.getFirst().code()).isEqualTo("STANDARD");
        assertThat(plans.getFirst().monthlyFee()).isEqualByComparingTo("299.00");
        assertThat(plans.getFirst().yearlyFee()).isEqualByComparingTo("2990.00");
        assertThat(plans.getFirst().maxStations()).isEqualTo(1);
        assertThat(plans.getLast().yearlyFee()).isEqualByComparingTo("8990.00");
        assertThat(plans.getLast().includesInvoicing()).isTrue();
    }

    // ------------------------------------------------------------------
    // changePlan
    // ------------------------------------------------------------------

    @Test
    void changePlan_existingActive_cancelsOldAndSavesNewInsteadOfUpdatingInPlace() {
        SubscriptionPlan pro = plan("PRO", "499.00", 2, true, false);
        Subscription current = subscription(plan("STANDARD", "299.00", 1, false, false), BillingCycle.MONTHLY);
        givenPlanLookup("PRO", pro);
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(1L);
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(current));
        givenSaveEchoesSubscription();

        SubscriptionResponse response = subscriptionService.changePlan(STORE_ID, "PRO", BillingCycle.MONTHLY);

        // Old row demoted, not reused.
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved).isNotSameAs(current);
        assertThat(saved.getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getPlan()).isSameAs(pro);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(saved.getNextBillingDate()).isEqualTo(saved.getStartDate().plusMonths(1));

        assertThat(response.planCode()).isEqualTo("PRO");
        assertThat(response.amountDue()).isEqualByComparingTo("499.00");
        assertThat(response.maxStations()).isEqualTo(2);
        assertThat(response.includesNotifications()).isTrue();
    }

    @Test
    void changePlan_cancelIsFlushedBeforeTheNewRowIsInserted() {
        // The partial unique index idx_one_active_subscription rejects two ACTIVE rows,
        // and Hibernate orders INSERTs before UPDATEs — so the CANCEL must be flushed first.
        givenPlanLookup("PRO", plan("PRO", "499.00", 2, true, false));
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(0L);
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription(plan("STANDARD", "299.00", 1, false, false),
                        BillingCycle.MONTHLY)));
        givenSaveEchoesSubscription();

        subscriptionService.changePlan(STORE_ID, "PRO", BillingCycle.MONTHLY);

        InOrder order = inOrder(subscriptionRepository);
        order.verify(subscriptionRepository).saveAndFlush(any(Subscription.class));
        order.verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void changePlan_noExistingSubscription_savesFirstOneWithoutCanceling() {
        givenPlanLookup("STANDARD", plan("STANDARD", "299.00", 1, false, false));
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(0L);
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());
        givenSaveEchoesSubscription();

        SubscriptionResponse response = subscriptionService.changePlan(STORE_ID, "STANDARD", BillingCycle.MONTHLY);

        assertThat(response.planCode()).isEqualTo("STANDARD");
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePlan_yearlyCycle_billsTenMonthsAndAdvancesOneYear() {
        givenPlanLookup("BUSINESS", plan("BUSINESS", "899.00", 5, true, true));
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(3L);
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());
        givenSaveEchoesSubscription();

        SubscriptionResponse response = subscriptionService.changePlan(STORE_ID, "BUSINESS", BillingCycle.YEARLY);

        assertThat(response.amountDue()).isEqualByComparingTo("8990.00");
        assertThat(response.nextBillingDate()).isEqualTo(response.startDate().plusYears(1));
        assertThat(response.includesInvoicing()).isTrue();
    }

    @Test
    void changePlan_unknownPlanCode_throwsPlanNotFound_andSavesNothing() {
        given(subscriptionPlanRepository.findByCodeAndActiveTrue("ENTERPRISE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.changePlan(STORE_ID, "ENTERPRISE", BillingCycle.MONTHLY))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("plan_not_found");

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePlan_downgradeBelowCurrentStationCount_throwsStationLimitExceeded() {
        // 3 active stations cannot fit STANDARD's cap of 1.
        givenPlanLookup("STANDARD", plan("STANDARD", "299.00", 1, false, false));
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(3L);

        assertThatThrownBy(() -> subscriptionService.changePlan(STORE_ID, "STANDARD", BillingCycle.MONTHLY))
                .isInstanceOf(InvalidResourceStateException.class)
                .extracting("code").isEqualTo("station_limit_exceeded");

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePlan_stationCountExactlyAtCap_isAllowed() {
        givenPlanLookup("PRO", plan("PRO", "499.00", 2, true, false));
        given(storeStationRepository.countByStoreIdAndStatus(STORE_ID, StoreStationStatus.ACTIVE)).willReturn(2L);
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());
        givenSaveEchoesSubscription();

        assertThat(subscriptionService.changePlan(STORE_ID, "PRO", BillingCycle.MONTHLY).planCode())
                .isEqualTo("PRO");
    }

    @Test
    void changePlan_storeMissing_propagatesStoreNotFound() {
        given(storeService.findById(STORE_ID)).willThrow(NotFoundException.store(STORE_ID));

        assertThatThrownBy(() -> subscriptionService.changePlan(STORE_ID, "PRO", BillingCycle.MONTHLY))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("store_not_found");

        verify(subscriptionRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // validateStationQuota
    // ------------------------------------------------------------------

    @Test
    void validateStationQuota_roomForOneMore_passes() {
        givenActiveSubscription(plan("PRO", "499.00", 2, true, false));

        subscriptionService.validateStationQuota(STORE_ID, 1L);
    }

    @Test
    void validateStationQuota_alreadyAtCap_throwsStationLimitExceeded() {
        givenActiveSubscription(plan("PRO", "499.00", 2, true, false));

        assertThatThrownBy(() -> subscriptionService.validateStationQuota(STORE_ID, 2L))
                .isInstanceOf(InvalidResourceStateException.class)
                .extracting("code").isEqualTo("station_limit_exceeded");
    }

    @Test
    void validateStationQuota_noActiveSubscription_throwsSubscriptionNotFound() {
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.validateStationQuota(STORE_ID, 0L))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("subscription_not_found");
    }

    // ------------------------------------------------------------------
    // getActiveSubscription
    // ------------------------------------------------------------------

    @Test
    void getActiveSubscription_returnsPlanEntitlements() {
        givenActiveSubscription(plan("BUSINESS", "899.00", 5, true, true));

        SubscriptionResponse response = subscriptionService.getActiveSubscription(STORE_ID);

        assertThat(response.planCode()).isEqualTo("BUSINESS");
        assertThat(response.maxStations()).isEqualTo(5);
        assertThat(response.includesNotifications()).isTrue();
        assertThat(response.includesInvoicing()).isTrue();
    }

    // ------------------------------------------------------------------
    // registerMonthlyPayment
    // ------------------------------------------------------------------

    @Test
    void registerMonthlyPayment_advancesOneMonth_persistsPayment_setsActive_andActivatesStore() {
        Subscription subscription = subscription(plan("PRO", "499.00", 2, true, false), BillingCycle.MONTHLY);
        given(subscriptionRepository.findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                eq(STORE_ID), anyCollection()))
                .willReturn(Optional.of(subscription));
        givenSaveEchoesPayment();

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
        assertThat(saved.getSubscriptionId()).isEqualTo(subscription.getId());
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
    void registerMonthlyPayment_yearlySubscription_usesYearlyFeeConceptAndOneYearPeriod() {
        Subscription subscription = subscription(plan("PRO", "499.00", 2, true, false), BillingCycle.YEARLY);
        given(subscriptionRepository.findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                eq(STORE_ID), anyCollection()))
                .willReturn(Optional.of(subscription));
        givenSaveEchoesPayment();

        SaasPaymentResponse response = subscriptionService.registerMonthlyPayment(
                STORE_ID, new BigDecimal("4990.00"), PaymentMethod.TRANSFER, "Anual 2026");

        assertThat(subscription.getNextBillingDate()).isEqualTo(NEXT_BILLING.plusYears(1));
        assertThat(response.concept()).isEqualTo(SaasPaymentConcept.YEARLY_FEE);
        assertThat(response.periodStart()).isEqualTo(NEXT_BILLING);
        assertThat(response.periodEnd()).isEqualTo(NEXT_BILLING.plusYears(1));
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

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // listUpcomingBillings / summary
    // ------------------------------------------------------------------

    @Test
    void listUpcomingBillings_yearlySubscriber_pricesTenMonthsAndAttachesStoreContact() {
        SubscriptionPlan pro = plan("PRO", "499.00", 2, true, false);
        given(subscriptionRepository.findUpcomingBillings(any()))
                .willReturn(List.of(dueOn(pro, BillingCycle.YEARLY, LocalDate.of(2026, 8, 14))));
        given(storeRepository.findAllById(List.of(STORE_ID)))
                .willReturn(List.of(store("Abarrotes La Pasadita", "5512345678")));

        List<UpcomingBillingResponse> billings = subscriptionService.listUpcomingBillings();

        assertThat(billings).hasSize(1);
        UpcomingBillingResponse row = billings.getFirst();
        assertThat(row.storeId()).isEqualTo(STORE_ID);
        assertThat(row.storeName()).isEqualTo("Abarrotes La Pasadita");
        assertThat(row.whatsapp()).isEqualTo("5512345678");
        assertThat(row.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        // The whole point of the DTO: 10 x monthly, not the plan's raw monthly_fee.
        assertThat(row.amountDue()).isEqualByComparingTo("4990.00");

        ArgumentCaptor<LocalDate> threshold = ArgumentCaptor.forClass(LocalDate.class);
        verify(subscriptionRepository).findUpcomingBillings(threshold.capture());
        assertThat(threshold.getValue()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @Test
    void listUpcomingBillings_nothingDue_returnsEmptyWithoutLookingUpStores() {
        given(subscriptionRepository.findUpcomingBillings(any())).willReturn(List.of());

        assertThat(subscriptionService.listUpcomingBillings()).isEmpty();

        verify(storeRepository, never()).findAllById(any());
    }

    @Test
    void summary_seedsEveryActiveTierAtZeroAndDerivesDueFiguresFromTheSameQuery() {
        SubscriptionPlan standard = plan("STANDARD", "299.00", 1, false, false);
        SubscriptionPlan pro = plan("PRO", "499.00", 2, true, false);
        SubscriptionPlan business = plan("BUSINESS", "899.00", 5, true, true);

        given(subscriptionRepository.findUpcomingBillings(any())).willReturn(List.of(
                dueOn(pro, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 12)),
                dueOn(business, BillingCycle.YEARLY, LocalDate.of(2026, 8, 14))));
        given(storeRepository.findAllById(List.of(STORE_ID)))
                .willReturn(List.of(store("Abarrotes La Pasadita", "5512345678")));
        given(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).willReturn(4L);
        given(subscriptionRepository.countActiveByPlanCode())
                .willReturn(List.of(new TierCount("PRO", 3L), new TierCount("BUSINESS", 1L)));
        given(subscriptionPlanRepository.findByActiveTrueOrderByMonthlyFeeAsc())
                .willReturn(List.of(standard, pro, business));

        SubscriptionSummaryResponse summary = subscriptionService.summary();

        assertThat(summary.activeSubscriptions()).isEqualTo(4L);
        assertThat(summary.dueNext7Days()).isEqualTo(2L);
        assertThat(summary.dueNext7DaysAmount()).isEqualByComparingTo("9489.00"); // 499 + 8990
        // Empty tiers stay visible, cheapest first, so the KPI card never silently drops one.
        assertThat(summary.tierDistribution())
                .containsExactly(entry("STANDARD", 0L), entry("PRO", 3L), entry("BUSINESS", 1L));
    }

    private Subscription dueOn(SubscriptionPlan plan, BillingCycle billingCycle, LocalDate nextBillingDate) {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .storeId(STORE_ID)
                .plan(plan)
                .billingCycle(billingCycle)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(nextBillingDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    private Store store(String name, String whatsapp) {
        return Store.builder()
                .id(STORE_ID)
                .code("la-pasadita")
                .name(name)
                .whatsapp(whatsapp)
                .build();
    }

    /**
     * Components are named after the projection's getters so the record's generated accessors
     * satisfy {@link PlanTierCount} directly.
     */
    private record TierCount(String getPlanCode, long getTotal) implements PlanTierCount {
    }

    private SubscriptionPlan plan(String code, String monthlyFee, int maxStations,
                                  boolean notifications, boolean invoicing) {
        return SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code)
                .monthlyFee(new BigDecimal(monthlyFee))
                .setupFee(BigDecimal.ZERO)
                .maxStations(maxStations)
                .includesNotifications(notifications)
                .includesInvoicing(invoicing)
                .active(true)
                .build();
    }

    private Subscription subscription(SubscriptionPlan plan, BillingCycle billingCycle) {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .storeId(STORE_ID)
                .plan(plan)
                .billingCycle(billingCycle)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(SubscriptionServiceTest.NEXT_BILLING)
                .status(SubscriptionStatus.OVERDUE)
                .build();
    }

    private void givenPlanLookup(String code, SubscriptionPlan plan) {
        given(subscriptionPlanRepository.findByCodeAndActiveTrue(code)).willReturn(Optional.of(plan));
    }

    private void givenActiveSubscription(SubscriptionPlan plan) {
        given(subscriptionRepository.findByStoreIdAndStatus(STORE_ID, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription(plan, BillingCycle.MONTHLY)));
    }

    private void givenSaveEchoesSubscription() {
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
    }

    private void givenSaveEchoesPayment() {
        given(saasPaymentRepository.save(any(SaasPayment.class))).willAnswer(inv -> {
            SaasPayment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }
}
