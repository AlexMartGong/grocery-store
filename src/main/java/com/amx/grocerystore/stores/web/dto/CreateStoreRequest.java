package com.amx.grocerystore.stores.web.dto;

import com.amx.grocerystore.stores.domain.AdminNotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$")
        String code,

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 150)
        String ownerName,

        @Size(max = 20)
        String whatsapp,

        @Size(max = 50)
        String telegramChatId,

        AdminNotificationChannel preferredAdminChannel,

        String address
) {
}
