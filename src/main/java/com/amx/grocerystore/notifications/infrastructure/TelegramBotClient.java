package com.amx.grocerystore.notifications.infrastructure;

import com.amx.grocerystore.notifications.infrastructure.dto.TelegramMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Slf4j
@Service
public class TelegramBotClient {

    private final RestClient restClient;
    private final String token;

    public TelegramBotClient(@Value("${telegram.bot.token:}") String token) {
        this.token = token;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .requestFactory(requestFactory)
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
        } catch (RestClientResponseException e) {
            // 4xx/5xx from Bot API — body carries the real reason (bad chat_id, token, blocked bot).
            log.error("Telegram API HTTP {} chatId={} body={}",
                    e.getStatusCode(), chatId, e.getResponseBodyAsString());
        } catch (Exception e) {
            // Timeouts (ResourceAccessException) and the rest — log full stacktrace, still swallow.
            log.error("Telegram API send failed chatId={}", chatId, e);
        }
    }
}
