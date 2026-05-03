package com.amx.grocerystore.products.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.products.application.ProductService;
import com.amx.grocerystore.products.web.dto.ProductRequest;
import com.amx.grocerystore.products.web.dto.ProductResponse;
import com.amx.grocerystore.products.web.dto.UpdateProductPricesRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(productService.list(active, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductResponse> updatePrices(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateProductPricesRequest request) {
        return ResponseEntity.ok(productService.updatePrices(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<ProductResponse> searchByBarcode(@RequestParam @NotBlank String barcode) {
        return ResponseEntity.ok(productService.findByBarcode(barcode));
    }
}
