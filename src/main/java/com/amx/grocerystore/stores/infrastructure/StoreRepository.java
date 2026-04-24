package com.amx.grocerystore.stores.infrastructure;

import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByCode(String code);

    boolean existsByCode(String code);

    Page<Store> findByStatus(StoreStatus status, Pageable pageable);
}
