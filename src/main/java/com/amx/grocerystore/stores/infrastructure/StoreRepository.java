package com.amx.grocerystore.stores.infrastructure;

import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByCode(String code);

    boolean existsByCode(String code);

    Page<Store> findByStatus(StoreStatus status, Pageable pageable);

    @Query("SELECT s FROM Store s WHERE (:status IS NULL OR s.status = :status) AND (:search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Store> findStores(@Param("status") String status,
                           @Param("search") String search,
                           Pageable pageable);
}
