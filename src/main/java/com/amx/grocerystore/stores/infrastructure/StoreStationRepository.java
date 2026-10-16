package com.amx.grocerystore.stores.infrastructure;

import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreStationRepository extends JpaRepository<StoreStation, UUID> {

    List<StoreStation> findByStoreIdAndStatusNot(UUID storeId, StoreStationStatus status);

    long countByStoreIdAndStatus(UUID storeId, StoreStationStatus status);
}
