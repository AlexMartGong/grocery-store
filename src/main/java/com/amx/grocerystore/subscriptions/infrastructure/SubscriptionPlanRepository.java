package com.amx.grocerystore.subscriptions.infrastructure;

import com.amx.grocerystore.subscriptions.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCodeAndActiveTrue(String code);

    List<SubscriptionPlan> findByActiveTrueOrderByMonthlyFeeAsc();
}
