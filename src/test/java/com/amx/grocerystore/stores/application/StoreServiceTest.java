package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.stores.web.dto.CreateStoreRequest;
import com.amx.grocerystore.stores.web.dto.StoreResponse;
import com.amx.grocerystore.stores.web.dto.UpdateStoreRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private StoreService storeService;

    private Store store(UUID id, String code, StoreStatus status) {
        return Store.builder()
                .id(id)
                .code(code)
                .name("Tienda")
                .status(status)
                .build();
    }

    @Test
    void create_savesWithActiveStatus_returnsResponse() {
        CreateStoreRequest req = new CreateStoreRequest("tienda-demo", "Tienda Demo", null, null, null);
        given(storeRepository.existsByCode("tienda-demo")).willReturn(false);
        given(storeRepository.save(any(Store.class))).willAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        StoreResponse response = storeService.create(req);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(captor.getValue().getCode()).isEqualTo("tienda-demo");
        assertThat(response.status()).isEqualTo(StoreStatus.ACTIVE);
    }

    @Test
    void create_whenCodeExists_throwsDuplicate() {
        CreateStoreRequest req = new CreateStoreRequest("tienda-demo", "Tienda Demo", null, null, null);
        given(storeRepository.existsByCode("tienda-demo")).willReturn(true);

        assertThatThrownBy(() -> storeService.create(req))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("store_code_taken");

        verify(storeRepository, never()).save(any());
    }

    @Test
    void findById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(storeRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.findById(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("store_not_found");
    }

    @Test
    void list_noFilter_delegatesFindStores_withNullStatus_andEmptySearch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Store> page = new PageImpl<>(List.of(store(UUID.randomUUID(), "x", StoreStatus.ACTIVE)));
        given(storeRepository.findStores(null, "", pageable)).willReturn(page);

        PageResponse<StoreResponse> response = storeService.list(null, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(storeRepository).findStores(null, "", pageable);
    }

    @Test
    void list_withStatus_delegatesFindStores_withStatusName() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Store> page = new PageImpl<>(List.of(store(UUID.randomUUID(), "x", StoreStatus.SUSPENDED)));
        given(storeRepository.findStores("SUSPENDED", "", pageable)).willReturn(page);

        PageResponse<StoreResponse> response = storeService.list(StoreStatus.SUSPENDED, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(storeRepository).findStores("SUSPENDED", "", pageable);
    }

    @Test
    void list_withSearch_passesTermVerbatim() {
        Pageable pageable = PageRequest.of(0, 10);
        given(storeRepository.findStores(null, "demo", pageable)).willReturn(new PageImpl<>(List.of()));

        storeService.list(null, "demo", pageable);

        verify(storeRepository).findStores(null, "demo", pageable);
    }

    @Test
    void update_modifiesAllowedFields_notCode() {
        UUID id = UUID.randomUUID();
        Store existing = store(id, "tienda-demo", StoreStatus.ACTIVE);
        given(storeRepository.findById(id)).willReturn(Optional.of(existing));
        given(storeRepository.save(any(Store.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateStoreRequest req = new UpdateStoreRequest("Nuevo Nombre", "Dueno", "55512345", "Calle 1", StoreStatus.SUSPENDED);
        StoreResponse response = storeService.update(id, req);

        assertThat(existing.getCode()).isEqualTo("tienda-demo");
        assertThat(response.name()).isEqualTo("Nuevo Nombre");
        assertThat(response.status()).isEqualTo(StoreStatus.SUSPENDED);
        assertThat(response.ownerName()).isEqualTo("Dueno");
    }

    @Test
    void softDelete_active_setsCanceledAndSaves() {
        UUID id = UUID.randomUUID();
        Store existing = store(id, "x", StoreStatus.ACTIVE);
        given(storeRepository.findById(id)).willReturn(Optional.of(existing));

        storeService.softDelete(id);

        assertThat(existing.getStatus()).isEqualTo(StoreStatus.CANCELED);
        verify(storeRepository).save(existing);
    }

    @Test
    void softDelete_alreadyCanceled_returnsEarly_noSave() {
        UUID id = UUID.randomUUID();
        Store existing = store(id, "x", StoreStatus.CANCELED);
        given(storeRepository.findById(id)).willReturn(Optional.of(existing));

        storeService.softDelete(id);

        verify(storeRepository, never()).save(any());
    }

    @Test
    void softDelete_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(storeRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.softDelete(id))
                .isInstanceOf(NotFoundException.class);
    }
}
