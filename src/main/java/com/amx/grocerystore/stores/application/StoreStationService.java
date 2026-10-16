package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.infrastructure.StoreStationRepository;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreStationService {

    private final StoreRepository storeRepository;
    private final StoreStationRepository storeStationRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public StoreStationResponse createStation(UUID storeId, String name) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));

        // The paid tier is the gate: a store with no ACTIVE subscription gets 404
        // subscription_not_found, one at its plan's cap gets 409 station_limit_exceeded.
        long activeStations = storeStationRepository.countByStoreIdAndStatus(storeId, StoreStationStatus.ACTIVE);
        subscriptionService.validateStationQuota(storeId, activeStations);

        StoreStation station = StoreStation.builder()
                .store(store)
                .name(name)
                .status(StoreStationStatus.ACTIVE)
                .agentUrl(null)
                .build();
        return StoreStationResponse.from(storeStationRepository.save(station));
    }
}
