package com.amx.grocerystore.sales.infrastructure;

import com.amx.grocerystore.sales.domain.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query("SELECT s FROM Sale s WHERE s.storeId = :storeId ORDER BY s.createdAt DESC")
    Page<Sale> findByStoreId(@Param("storeId") UUID storeId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(s.amountPaid), 0) FROM Sale s
            WHERE s.storeId = :storeId
              AND s.paymentMethod IN (com.amx.grocerystore.sales.domain.SalePaymentMethod.CASH,
                                      com.amx.grocerystore.sales.domain.SalePaymentMethod.MIXED)
              AND s.createdAt >= :openedAt
              AND s.status = com.amx.grocerystore.sales.domain.SaleStatus.COMPLETED
            """)
    BigDecimal sumCashInflow(@Param("storeId") UUID storeId, @Param("openedAt") OffsetDateTime openedAt);
}
