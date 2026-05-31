package com.amx.grocerystore.dashboard.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.dashboard.application.DashboardService;
import com.amx.grocerystore.dashboard.web.dto.ProductToRestockResponse;
import com.amx.grocerystore.dashboard.web.dto.SalesTodayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/sales-today")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<SalesTodayResponse> salesToday() {
        return ResponseEntity.ok(dashboardService.getSalesToday());
    }

    @GetMapping("/products-to-restock")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PageResponse<ProductToRestockResponse>> productsToRestock(
            @PageableDefault() Pageable pageable) {
        return ResponseEntity.ok(dashboardService.getProductsToRestock(pageable));
    }
}
