package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-facing view of the store's own subscription, so the PWA can gate features
 * on the plan's entitlements. Store comes from the JWT — never from the client.
 */
@RestController
@RequestMapping("/api/v1/stores/me/subscription")
@RequiredArgsConstructor
public class MySubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<SubscriptionResponse> current() {
        return ResponseEntity.ok(subscriptionService.getActiveSubscription(SecurityUtils.currentStoreId()));
    }
}
