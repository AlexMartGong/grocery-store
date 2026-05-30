package com.amx.grocerystore.subscriptions.infrastructure;

import com.amx.grocerystore.subscriptions.domain.Subscription;
import com.amx.grocerystore.subscriptions.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByStatusAndNextBillingDateLessThan(SubscriptionStatus status, LocalDate threshold);

    Optional<Subscription> findFirstByStoreIdAndStatusInOrderByNextBillingDateDesc(
            UUID storeId, Collection<SubscriptionStatus> statuses);
}
