package com.amx.grocerystore.subscriptions.web.dto;

import com.amx.grocerystore.subscriptions.domain.BillingCycle;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePlanRequest(
        @NotBlank
        @Size(max = 30)
        @JsonProperty("plan_code")
        String planCode,

        @NotNull
        @JsonProperty("billing_cycle")
        BillingCycle billingCycle) {
}
