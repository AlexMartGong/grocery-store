package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.stores.application.StoreStationService;
import com.amx.grocerystore.stores.web.dto.CreateStoreStationRequest;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}/stations")
@RequiredArgsConstructor
public class AdminStoreStationController {

    private final StoreStationService storeStationService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<StoreStationResponse> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody CreateStoreStationRequest request) {
        StoreStationResponse response = storeStationService.createStation(storeId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
