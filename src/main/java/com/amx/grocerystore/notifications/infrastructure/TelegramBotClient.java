package com.amx.grocerystore.notifications.infrastructure;

import com.amx.grocerystore.notifications.infrastructure.dto.TelegramMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class TelegramBotClient {

    private final RestClient restClient;
    private final String token;

    public TelegramBotClient(@Value("${telegram.bot.token:}") String token) {
        this.token = token;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public void sendMessage(String chatId, String text) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Skip Telegram send: token or chatId empty (chatId={})", chatId);
            return;
        }
        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TelegramMessageRequest(chatId, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Telegram API send failed chatId={} err={}", chatId, e.getMessage());
        }
    }
}
