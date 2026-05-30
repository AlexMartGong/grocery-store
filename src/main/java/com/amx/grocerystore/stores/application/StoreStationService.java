package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.infrastructure.StoreStationRepository;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreStationService {

    private final StoreRepository storeRepository;
    private final StoreStationRepository storeStationRepository;

    @Transactional
    public StoreStationResponse createStation(UUID storeId, String name) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));
        StoreStation station = StoreStation.builder()
                .store(store)
                .name(name)
                .status(StoreStationStatus.ACTIVE)
                .agentUrl(null)
                .build();
        return StoreStationResponse.from(storeStationRepository.save(station));
    }
}
