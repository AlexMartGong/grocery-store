package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.AdminNotificationChannel;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.infrastructure.StoreStationRepository;
import com.amx.grocerystore.stores.web.dto.CreateStoreRequest;
import com.amx.grocerystore.stores.web.dto.StoreProfileResponse;
import com.amx.grocerystore.stores.web.dto.StoreResponse;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import com.amx.grocerystore.stores.web.dto.UpdateStoreRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreStationRepository storeStationRepository;

    @Transactional
    public StoreResponse create(CreateStoreRequest request) {
        if (storeRepository.existsByCode(request.code())) {
            throw DuplicateResourceException.storeCode(request.code());
        }
        Store store = Store.builder()
                .code(request.code())
                .name(request.name())
                .ownerName(request.ownerName())
                .whatsapp(request.whatsapp())
                .telegramChatId(request.telegramChatId())
                .preferredAdminChannel(request.preferredAdminChannel() != null
                        ? request.preferredAdminChannel()
                        : AdminNotificationChannel.WHATSAPP)
                .address(request.address())
                .status(StoreStatus.ACTIVE)
                .build();
        return StoreResponse.from(storeRepository.save(store));
    }

    @Transactional(readOnly = true)
    public StoreResponse findById(UUID id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.store(id));
        return StoreResponse.from(store);
    }

    @Transactional(readOnly = true)
    public StoreProfileResponse getStoreProfile(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));
        return StoreProfileResponse.from(store);
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> list(StoreStatus statusFilter, String search, Pageable pageable) {
        String searchTerm = (search == null) ? "" : search;
        Page<Store> page = storeRepository.findStores(statusFilter, searchTerm, pageable);
        return PageResponse.from(page.map(StoreResponse::from));
    }

    @Transactional
    public StoreResponse update(UUID id, UpdateStoreRequest request) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.store(id));
        store.setName(request.name());
        store.setOwnerName(request.ownerName());
        store.setWhatsapp(request.whatsapp());
        store.setTelegramChatId(request.telegramChatId());
        store.setPreferredAdminChannel(request.preferredAdminChannel());
        store.setAddress(request.address());
        store.setStatus(request.status());
        return StoreResponse.from(storeRepository.save(store));
    }

    @Transactional
    public void updateAgentUrl(UUID stationId, String url) {
        StoreStation station = storeStationRepository.findById(stationId)
                .orElseThrow(() -> NotFoundException.station(stationId));
        station.setAgentUrl(url);
        storeStationRepository.save(station);
    }

    @Transactional(readOnly = true)
    public List<StoreStationResponse> getStations(UUID storeId) {
        return storeStationRepository
                .findByStoreIdAndStatusNot(storeId, StoreStationStatus.CANCELED)
                .stream()
                .map(StoreStationResponse::from)
                .toList();
    }

    @Transactional
    public void suspend(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));
        store.setStatus(StoreStatus.SUSPENDED);
    }

    @Transactional
    public void activate(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));
        store.setStatus(StoreStatus.ACTIVE);
    }

    @Transactional
    public void softDelete(UUID id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.store(id));
        if (store.getStatus() == StoreStatus.CANCELED) {
            return;
        }
        store.setStatus(StoreStatus.CANCELED);
        storeRepository.save(store);
    }
}
