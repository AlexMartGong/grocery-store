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
}
