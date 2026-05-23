package com.amx.grocerystore.sales.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.sales.application.SaleService;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.web.dto.CancelSaleRequest;
import com.amx.grocerystore.sales.web.dto.CreateSaleRequest;
import com.amx.grocerystore.sales.web.dto.SaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','CASHIER')")
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody CreateSaleRequest request) {
        SaleResponse response = saleService.createSale(request);
        return ResponseEntity.created(URI.create("/api/v1/sales/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<SaleResponse>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) SalePaymentMethod paymentMethod,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(
                saleService.list(customerId, paymentMethod, search, startDate, endDate, pageable));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, @Valid @RequestBody CancelSaleRequest request) {
        saleService.cancelSale(id, request);
    }
}
