package com.boangwan.digest;

import com.boangwan.config.AnthropicProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicClient {

    private final WebClient anthropicWebClient;
    private final AnthropicProperties anthropicProperties;
    private final ObjectMapper objectMapper;

    @Retryable(
            retryFor = {WebClientResponseException.class, WebClientRequestException.class},
            noRetryFor = {WebClientResponseException.BadRequest.class,
                          WebClientResponseException.Unauthorized.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String call(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, anthropicProperties.maxTokens());
    }

    @Retryable(
            retryFor = {WebClientResponseException.class, WebClientRequestException.class},
            noRetryFor = {WebClientResponseException.BadRequest.class,
                          WebClientResponseException.Unauthorized.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String call(String systemPrompt, String userMessage, int maxTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", anthropicProperties.model());
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", userMessage);

        String response = anthropicWebClient.post()
                .uri("/v1/messages")
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Anthropic API 응답 파싱 실패: " + response, e);
        }
    }
}
