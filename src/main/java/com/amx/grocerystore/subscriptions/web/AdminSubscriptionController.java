package com.amx.grocerystore.subscriptions.web;

import com.amx.grocerystore.subscriptions.application.SubscriptionService;
import com.amx.grocerystore.subscriptions.web.dto.SubscriptionSummaryResponse;
import com.amx.grocerystore.subscriptions.web.dto.UpcomingBillingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cross-tenant subscription reporting for the SaaS operator. Everything here is read-only —
 * subscription mutations live on {@link StoreSubscriptionController}, which is scoped to a store.
 */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Not paginated on purpose: the window is a handful of days wide, so the whole list fits in one
     * response and the client scrolls it.
     */
    @GetMapping("/upcoming-billings")
    public ResponseEntity<List<UpcomingBillingResponse>> upcomingBillings() {
        return ResponseEntity.ok(subscriptionService.listUpcomingBillings());
    }

    @GetMapping("/summary")
    public ResponseEntity<SubscriptionSummaryResponse> summary() {
        return ResponseEntity.ok(subscriptionService.summary());
    }
}
