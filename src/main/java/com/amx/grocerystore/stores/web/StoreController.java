package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.web.dto.CreateStoreRequest;
import com.amx.grocerystore.stores.web.dto.StoreResponse;
import com.amx.grocerystore.stores.web.dto.UpdateStoreRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody CreateStoreRequest request) {
        StoreResponse response = storeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/stores/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(storeService.findById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StoreResponse>> list(
            @RequestParam(required = false) StoreStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(storeService.list(status, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoreResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(storeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        storeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
