package com.amx.grocerystore.stores.infrastructure;

import com.amx.grocerystore.stores.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByCode(String code);
}
