package com.amx.grocerystore.customers.infrastructure;

import com.amx.grocerystore.customers.domain.CustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, UUID> {

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM CustomerPayment p
            WHERE p.storeId = :storeId
              AND p.createdAt >= :openedAt
            """)
    BigDecimal sumPaymentsSince(@Param("storeId") UUID storeId, @Param("openedAt") OffsetDateTime openedAt);
}
