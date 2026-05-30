package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.web.dto.RegisterSaasPaymentRequest;
import com.amx.grocerystore.subscriptions.web.dto.SaasPaymentResponse;
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
@RequestMapping("/api/v1/admin/stores/{storeId}/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SaasPaymentController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SaasPaymentResponse> register(@PathVariable UUID storeId,
                                                        @Valid @RequestBody RegisterSaasPaymentRequest request) {
        SaasPaymentResponse response = subscriptionService.registerMonthlyPayment(
                storeId, request.amount(), request.method(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
