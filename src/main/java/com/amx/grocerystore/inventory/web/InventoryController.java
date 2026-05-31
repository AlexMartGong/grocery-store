package com.amx.grocerystore.inventory.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.inventory.application.InventoryService;
import com.amx.grocerystore.inventory.web.dto.AdjustStockRequest;
import com.amx.grocerystore.inventory.web.dto.InventoryMovementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/movements")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<Void> adjustStock(@Valid @RequestBody AdjustStockRequest request) {
        inventoryService.adjustStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<PageResponse<InventoryMovementResponse>> getMovements(
            @RequestParam UUID productId,
            @PageableDefault() Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getProductMovements(productId, pageable));
    }
}
