package com.amx.grocerystore.notifications.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramMessageRequest(@JsonProperty("chat_id") String chatId, String text) {
}
