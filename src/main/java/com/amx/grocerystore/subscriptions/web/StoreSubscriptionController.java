package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.web.dto.ChangePlanRequest;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StoreSubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * The store's current plan, so the admin can see what they are changing from before they
     * change it. 404 {@code subscription_not_found} when the store has never subscribed —
     * callers treat that as "no plan", not as an error.
     */
    @GetMapping
    public ResponseEntity<SubscriptionResponse> current(@PathVariable UUID storeId) {
        return ResponseEntity.ok(subscriptionService.getActiveSubscription(storeId));
    }

    /**
     * Subscribes the store to a plan, or switches/renews it. Always 201 — every call
     * creates a new subscription row and cancels the previous one.
     */
    @PostMapping
    public ResponseEntity<SubscriptionResponse> changePlan(@PathVariable UUID storeId,
                                                           @Valid @RequestBody ChangePlanRequest request) {
        SubscriptionResponse response = subscriptionService.changePlan(
                storeId, request.planCode(), request.billingCycle());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
