package com.amx.grocerystore.subscriptions.infrastructure;

import com.amx.grocerystore.subscriptions.domain.SaasPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaasPaymentRepository extends JpaRepository<SaasPayment, UUID> {
}
