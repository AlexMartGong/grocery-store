package com.amx.grocerystore.core.web.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static NotFoundException store(UUID id) {
        return new NotFoundException("store_not_found", "Store not found: " + id);
    }

    public static NotFoundException user(UUID id) {
        return new NotFoundException("user_not_found", "User not found: " + id);
    }
}
