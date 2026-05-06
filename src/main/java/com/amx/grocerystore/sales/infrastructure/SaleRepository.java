package com.amx.grocerystore.sales.infrastructure;

import com.amx.grocerystore.sales.domain.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query("SELECT s FROM Sale s WHERE s.storeId = :storeId ORDER BY s.createdAt DESC")
    Page<Sale> findByStoreId(@Param("storeId") UUID storeId, Pageable pageable);
}
