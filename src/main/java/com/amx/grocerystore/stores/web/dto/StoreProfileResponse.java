package com.amx.grocerystore.stores.web.dto;

import com.amx.grocerystore.stores.domain.Store;

import java.util.UUID;

public record StoreProfileResponse(
        UUID id,
        String name,
        String address,
        String phone
) {

    public static StoreProfileResponse from(Store store) {
        return new StoreProfileResponse(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getWhatsapp()
        );
    }
}
