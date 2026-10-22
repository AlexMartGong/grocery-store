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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /**
     * How far ahead the admin collections view looks. Mirrored by the {@code v_upcoming_billings}
     * SQL view for ad-hoc queries — change both together.
     */
    private static final int UPCOMING_WINDOW_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SaasPaymentRepository saasPaymentRepository;
    private final StoreStationRepository storeStationRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listPlans() {
        return subscriptionPlanRepository.findByActiveTrueOrderByMonthlyFeeAsc()
                .stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getActiveSubscription(UUID storeId) {
        return SubscriptionResponse.from(requireActiveSubscription(storeId));
    }

    /**
     * Renewals the admin should be collecting: every ACTIVE subscription billing within the next
     * {@value #UPCOMING_WINDOW_DAYS} days, soonest first, with the store's WhatsApp number attached.
     */
    @Transactional(readOnly = true)
    public List<UpcomingBillingResponse> listUpcomingBillings() {
        return toUpcomingBillings(subscriptionRepository.findUpcomingBillings(upcomingThreshold()));
    }

    /**
     * Platform-wide KPIs. Derives the 7-day figures from the very same query that backs
     * {@link #listUpcomingBillings()}, so the header can never contradict the table under it.
     */
    @Transactional(readOnly = true)
    public SubscriptionSummaryResponse summary() {
        List<UpcomingBillingResponse> upcoming =
                toUpcomingBillings(subscriptionRepository.findUpcomingBillings(upcomingThreshold()));

        BigDecimal dueAmount = upcoming.stream()
                .map(UpcomingBillingResponse::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> counted = subscriptionRepository.countActiveByPlanCode().stream()
                .collect(Collectors.toMap(PlanTierCount::getPlanCode, PlanTierCount::getTotal));

        Map<String, Long> tierDistribution = new LinkedHashMap<>();
        subscriptionPlanRepository.findByActiveTrueOrderByMonthlyFeeAsc()
                .forEach(plan -> tierDistribution.put(plan.getCode(), counted.getOrDefault(plan.getCode(), 0L)));
        counted.forEach(tierDistribution::putIfAbsent);

        return new SubscriptionSummaryResponse(
                subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                upcoming.size(),
                dueAmount,
                tierDistribution);
    }

    /**
     * Subscribes a store to {@code planCode}, or switches/renews it onto that plan.
     * <p>
     * History is append-only: the current ACTIVE subscription is marked CANCELED and a
     * brand-new ACTIVE row is inserted, so past pricing and cycles stay auditable
     * alongside the payments that reference them. Never an in-place plan swap.
     */
    @Transactional
    public SubscriptionResponse changePlan(UUID storeId, String planCode, BillingCycle billingCycle) {
        storeService.findById(storeId);

        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeAndActiveTrue(planCode)
                .orElseThrow(() -> NotFoundException.plan(planCode));

        long activeStations = countActiveStations(storeId);
        if (activeStations > plan.getMaxStations()) {
            throw InvalidResourceStateException.stationLimitExceeded(plan.getMaxStations());
        }

        subscriptionRepository.findByStoreIdAndStatus(storeId, SubscriptionStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(SubscriptionStatus.CANCELED);
                    subscriptionRepository.saveAndFlush(current);
                });

        LocalDate startDate = LocalDate.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .storeId(storeId)
                .plan(plan)
                .billingCycle(billingCycle)
                .startDate(startDate)
                .nextBillingDate(billingCycle.advance(startDate))
                .status(SubscriptionStatus.ACTIVE)
                .build());

        return SubscriptionResponse.from(subscription);
    }

    /**
     * Asserts the store's plan has room for one more active station.
     *
     * @param storeId                    the store about to gain a station
     * @param currentActiveStationsCount stations the store <em>already</em> has active;
     *                                   the check reserves a slot for the one being added
     * @throws NotFoundException                if the store has no ACTIVE subscription
     * @throws InvalidResourceStateException    {@code station_limit_exceeded} if the plan is full
     */
    @Transactional(readOnly = true)
    public void validateStationQuota(UUID storeId, long currentActiveStationsCount) {
        Subscription subscription = requireActiveSubscription(storeId);
        int maxStations = subscription.getPlan().getMaxStations();
        if (currentActiveStationsCount >= maxStations) {
            throw InvalidResourceStateException.stationLimitExceeded(maxStations);
        }
    }

    /**
     * Records a collected SaaS fee and rolls the billing window forward.
     * <p>
     * Unlike {@link #changePlan}, this updates the subscription in place — collecting a
     * period's fee is not a subscription change, and a new row per payment would churn
     * the single-ACTIVE index for no audit gain (the payment row is the audit record).
     */
    @Transactional
    public SaasPaymentResponse registerMonthlyPayment(UUID storeId, BigDecimal amount,
                                                      PaymentMethod method, String notes) {
        Subscription subscription = subscriptionRepository
                .findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
                        storeId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.OVERDUE))
                .orElseThrow(() -> NotFoundException.subscription(storeId));

        BillingCycle billingCycle = subscription.getBillingCycle();
        LocalDate periodStart = subscription.getNextBillingDate();
        LocalDate periodEnd = billingCycle.advance(periodStart);

        SaasPayment payment = saasPaymentRepository.save(SaasPayment.builder()
                .storeId(storeId)
                .subscriptionId(subscription.getId())
                .amount(amount)
                .concept(billingCycle.paymentConcept())
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

    private LocalDate upcomingThreshold() {
        return LocalDate.now().plusDays(UPCOMING_WINDOW_DAYS);
    }

    /**
     * Prices each due subscription and joins in its store. Stores are looked up in one batch rather
     * than through an entity join because {@code Subscription.storeId} is a bare UUID, not an
     * association.
     */
    private List<UpcomingBillingResponse> toUpcomingBillings(List<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        List<UUID> storeIds = subscriptions.stream()
                .map(Subscription::getStoreId)
                .distinct()
                .toList();
        Map<UUID, Store> stores = storeRepository.findAllById(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));

        return subscriptions.stream()
                .map(subscription -> {
                    Store store = stores.get(subscription.getStoreId());
                    BillingCycle cycle = subscription.getBillingCycle();
                    return new UpcomingBillingResponse(
                            subscription.getStoreId(),
                            store == null ? null : store.getName(),
                            store == null ? null : store.getWhatsapp(),
                            subscription.getNextBillingDate(),
                            cycle,
                            cycle.feeFor(subscription.getPlan().getMonthlyFee()));
                })
                .toList();
    }

    private Subscription requireActiveSubscription(UUID storeId) {
        return subscriptionRepository.findByStoreIdAndStatus(storeId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.subscription(storeId));
    }

    private long countActiveStations(UUID storeId) {
        return storeStationRepository.countByStoreIdAndStatus(storeId, StoreStationStatus.ACTIVE);
    }
}
