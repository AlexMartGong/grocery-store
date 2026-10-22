package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.infrastructure.StoreStationRepository;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreStationServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreStationRepository storeStationRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private StoreStationService storeStationService;

    private Store store(UUID id) {
        return Store.builder()
                .id(id)
                .code("tienda-demo")
                .name("Tienda")
                .status(StoreStatus.ACTIVE)
                .build();
    }

    @Test
    void createStation_storeExists_savesActiveStation() {
        UUID storeId = UUID.randomUUID();
        Store store = store(storeId);
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
        given(storeStationRepository.countByStoreIdAndStatus(storeId, StoreStationStatus.ACTIVE)).willReturn(1L);
        given(storeStationRepository.save(any(StoreStation.class))).willAnswer(inv -> {
            StoreStation s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        StoreStationResponse response = storeStationService.createStation(storeId, "Caja 1");

        ArgumentCaptor<StoreStation> captor = ArgumentCaptor.forClass(StoreStation.class);
        verify(storeStationRepository).save(captor.capture());
        StoreStation saved = captor.getValue();
        assertThat(saved.getStore()).isSameAs(store);
        assertThat(saved.getName()).isEqualTo("Caja 1");
        assertThat(saved.getStatus()).isEqualTo(StoreStationStatus.ACTIVE);
        assertThat(saved.getAgentUrl()).isNull();

        assertThat(response.name()).isEqualTo("Caja 1");
        assertThat(response.status()).isEqualTo(StoreStationStatus.ACTIVE);
        assertThat(response.agentUrl()).isNull();
        assertThat(response.id()).isNotNull();

        // Quota checked against the count that already exists, before persisting.
        verify(subscriptionService).validateStationQuota(storeId, 1L);
    }

    @Test
    void createStation_planQuotaExhausted_throwsAndSavesNothing() {
        UUID storeId = UUID.randomUUID();
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store(storeId)));
        given(storeStationRepository.countByStoreIdAndStatus(storeId, StoreStationStatus.ACTIVE)).willReturn(2L);
        willThrow(InvalidResourceStateException.stationLimitExceeded(2))
                .given(subscriptionService).validateStationQuota(storeId, 2L);

        assertThatThrownBy(() -> storeStationService.createStation(storeId, "Caja 3"))
                .isInstanceOf(InvalidResourceStateException.class)
                .extracting("code").isEqualTo("station_limit_exceeded");

        verify(storeStationRepository, never()).save(any());
    }

    @Test
    void createStation_storeWithoutSubscription_propagatesSubscriptionNotFound() {
        UUID storeId = UUID.randomUUID();
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store(storeId)));
        given(storeStationRepository.countByStoreIdAndStatus(storeId, StoreStationStatus.ACTIVE)).willReturn(0L);
        willThrow(NotFoundException.subscription(storeId))
                .given(subscriptionService).validateStationQuota(storeId, 0L);

        assertThatThrownBy(() -> storeStationService.createStation(storeId, "Caja 1"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("subscription_not_found");

        verify(storeStationRepository, never()).save(any());
    }

    @Test
    void createStation_storeMissing_throwsNotFound() {
        UUID storeId = UUID.randomUUID();
        given(storeRepository.findById(storeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> storeStationService.createStation(storeId, "Caja 1"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("store_not_found");

        verify(storeStationRepository, never()).save(any());
    }
}
