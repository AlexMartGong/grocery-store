package com.amx.grocerystore.core.web.exception;

import lombok.Getter;

@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String code;

    public DuplicateResourceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static DuplicateResourceException storeCode(String code) {
        return new DuplicateResourceException("store_code_taken", "Store code already in use: " + code);
    }

    public static DuplicateResourceException username(String username) {
        return new DuplicateResourceException("username_taken", "Username already in use: " + username);
    }

    public static DuplicateResourceException storeOwner(java.util.UUID storeId) {
        return new DuplicateResourceException("store_owner_exists",
                "Store already has an active owner: " + storeId);
    }

    public static DuplicateResourceException barcode(String barcode) {
        return new DuplicateResourceException("product_barcode_taken",
                "Barcode already in use: " + barcode);
    }
}
