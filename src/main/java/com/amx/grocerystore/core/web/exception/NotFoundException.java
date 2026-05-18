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

    public static NotFoundException product(UUID id) {
        return new NotFoundException("product_not_found", "Product not found: " + id);
    }

    public static NotFoundException productByBarcode(String barcode) {
        return new NotFoundException("product_not_found", "Product not found for barcode: " + barcode);
    }

    public static NotFoundException customer(UUID id) {
        return new NotFoundException("customer_not_found", "Customer not found: " + id);
    }

    public static NotFoundException openShift() {
        return new NotFoundException("open_shift_not_found", "No open cash register shift for current store.");
    }

    public static NotFoundException notification(UUID id) {
        return new NotFoundException("notification_not_found", "Notification not found: " + id);
    }

    public static NotFoundException sale(UUID id) {
        return new NotFoundException("sale_not_found", "Sale not found: " + id);
    }

    public static NotFoundException station(UUID id) {
        return new NotFoundException("station_not_found", "Store station not found: " + id);
    }
}
