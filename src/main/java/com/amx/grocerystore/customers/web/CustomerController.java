package com.amx.grocerystore.customers.web;

import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.customers.application.CustomerService;
import com.amx.grocerystore.customers.web.dto.CreateCustomerRequest;
import com.amx.grocerystore.customers.web.dto.CreatePaymentRequest;
import com.amx.grocerystore.customers.web.dto.CustomerPaymentResponse;
import com.amx.grocerystore.customers.web.dto.CustomerResponse;
import com.amx.grocerystore.customers.web.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','CASHIER')")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(customerService.list(active, search, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('OWNER','CASHIER')")
    public ResponseEntity<CustomerPaymentResponse> registerPayment(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePaymentRequest request) {
        CustomerPaymentResponse response = customerService.registerPayment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
