package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.web.dto.AgentUrlResponse;
import com.amx.grocerystore.stores.web.dto.StoreProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/me/agent")
    @PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
    public ResponseEntity<AgentUrlResponse> getAgentUrl() {
        return ResponseEntity.ok(storeService.getAgentUrl(SecurityUtils.currentStoreId()));
    }
}
