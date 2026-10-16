package com.amx.grocerystore.subscriptions.infrastructure;

import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByStatusAndNextBillingDateLessThan(SubscriptionStatus status, LocalDate threshold);

    Optional<Subscription> findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
            UUID storeId, Collection<SubscriptionStatus> statuses);

    /**
     * At most one row can match with {@code status = ACTIVE} — the partial unique index
     * {@code idx_one_active_subscription} guarantees it.
     */
    Optional<Subscription> findByStoreIdAndStatus(UUID storeId, SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);

    /**
     * ACTIVE subscriptions billing on or before {@code threshold}, plan eagerly fetched so the
     * caller can price each one without an N+1 on the LAZY association.
     * <p>
     * Deliberately unbounded below: a subscription stays ACTIVE for
     * {@code SubscriptionBillingScheduler.GRACE_DAYS} after its date passes, and those already-late
     * rows are exactly the ones the admin most needs to see.
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            WHERE s.status = com.amx.grocerystore.subscriptions.domain.SubscriptionStatus.ACTIVE
              AND s.nextBillingDate <= :threshold
            ORDER BY s.nextBillingDate ASC
            """)
    List<Subscription> findUpcomingBillings(@Param("threshold") LocalDate threshold);

    @Query("""
            SELECT p.code AS planCode, COUNT(s) AS total
            FROM Subscription s
            JOIN s.plan p
            WHERE s.status = com.amx.grocerystore.subscriptions.domain.SubscriptionStatus.ACTIVE
            GROUP BY p.code
            """)
    List<PlanTierCount> countActiveByPlanCode();
}
