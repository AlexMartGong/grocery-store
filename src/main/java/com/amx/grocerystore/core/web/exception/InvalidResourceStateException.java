package com.amx.grocerystore.core.web.exception;

import com.amx.grocerystore.inventory.domain.InventoryReason;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.users.domain.UserRole;
import lombok.Getter;

import java.util.UUID;

@Getter
public class InvalidResourceStateException extends RuntimeException {

    private final String code;

    public InvalidResourceStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static InvalidResourceStateException storeNotActive(UUID storeId) {
        return new InvalidResourceStateException("store_not_active",
                "Store is not active: " + storeId);
    }

    public static InvalidResourceStateException roleNotAllowedForProvisioning(UserRole role) {
        return new InvalidResourceStateException("role_not_allowed",
                "Role not allowed for store provisioning: " + role);
    }

    public static InvalidResourceStateException reasonNotAllowed(InventoryReason reason) {
        return new InvalidResourceStateException("reason_not_allowed",
                "Inventory reason " + reason + " is managed by the system and cannot be set manually");
    }

    public static InvalidResourceStateException creditLimitExceeded() {
        return new InvalidResourceStateException("credit_limit_exceeded",
                "The sale exceeds the customer's credit limit.");
    }

    public static InvalidResourceStateException paymentMethodNotSupported(SalePaymentMethod method) {
        return new InvalidResourceStateException("payment_method_not_supported",
                "Payment method not supported: " + method);
    }

    public static InvalidResourceStateException customerRequiredForCredit() {
        return new InvalidResourceStateException("customer_required",
                "Customer is required for credit sales.");
    }
}
