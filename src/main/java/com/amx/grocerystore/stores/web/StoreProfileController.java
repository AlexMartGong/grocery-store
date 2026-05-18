package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.web.dto.StoreProfileResponse;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreProfileController {

    private final StoreService storeService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
    public ResponseEntity<StoreProfileResponse> getCurrent() {
        return ResponseEntity.ok(storeService.getStoreProfile(SecurityUtils.currentStoreId()));
    }

    @GetMapping("/me/stations")
    @PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
    public ResponseEntity<List<StoreStationResponse>> getStations() {
        return ResponseEntity.ok(storeService.getStations(SecurityUtils.currentStoreId()));
    }
}
