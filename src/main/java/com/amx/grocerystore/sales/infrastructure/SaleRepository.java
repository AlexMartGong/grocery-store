package com.amx.grocerystore.sales.infrastructure;

import com.amx.grocerystore.sales.domain.Sale;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query("SELECT s FROM Sale s WHERE s.storeId = :storeId ORDER BY s.createdAt DESC")
    Page<Sale> findByStoreId(@Param("storeId") UUID storeId, Pageable pageable);

    Optional<Sale> findByIdAndStoreId(UUID id, UUID storeId);

    @Query("""
            SELECT s FROM Sale s
            WHERE s.storeId = :storeId
              AND (:customerId IS NULL OR s.customerId = :customerId)
              AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod)
              AND (:search = '' OR CAST(s.id AS string) LIKE LOWER(CONCAT(:search, '%')))
              AND s.createdAt >= :startDate
              AND s.createdAt <= :endDate
            ORDER BY s.createdAt DESC
            """)
    Page<Sale> findSales(@Param("storeId") UUID storeId,
                         @Param("customerId") UUID customerId,
                         @Param("paymentMethod") SalePaymentMethod paymentMethod,
                         @Param("search") String search,
                         @Param("startDate") OffsetDateTime startDate,
                         @Param("endDate") OffsetDateTime endDate,
                         Pageable pageable);

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
