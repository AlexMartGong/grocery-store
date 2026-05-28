package com.amx.grocerystore.notifications.infrastructure;

import com.amx.grocerystore.notifications.infrastructure.dto.EvolutionMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Service
public class EvolutionWhatsAppClient {

    private final RestClient restClient;
    private final String instance;

    public EvolutionWhatsAppClient(
            @Value("${evolution.api.base-url}") String baseUrl,
            @Value("${evolution.api.apikey}") String apiKey,
            @Value("${evolution.api.instance}") String instance
    ) {
        this.instance = instance;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    public void sendMessage(String phone, String text) {
        if (phone == null) {
            log.warn("Skip WhatsApp send: phone is null");
            return;
        }
        String normalized = phone.replace(" ", "");
        if (normalized.length() == 10 && normalized.chars().allMatch(Character::isDigit)) {
            normalized = "52" + normalized;
        }
        try {
            restClient.post()
                    .uri("/message/sendText/{instance}", instance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EvolutionMessageRequest(normalized, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Evolution API send failed phone={} err={}", normalized, e.getMessage());
        }
    }
}
