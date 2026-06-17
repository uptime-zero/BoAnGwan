package com.boangwan.digest;

import com.boangwan.config.AnthropicProperties;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.RawArticle;
import com.boangwan.repository.DailyDigestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestGenerator {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AnthropicProperties anthropicProperties;
    private final DigestPromptBuilder promptBuilder;
    private final DigestParser digestParser;
    private final DailyDigestRepository dailyDigestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public DailyDigest generate(RawArticle article, LocalDate digestDate) {
        String responseJson = callClaudeApi(article);
        DigestParser.ParsedDigest parsed = digestParser.parse(responseJson);

        DailyDigest digest = DailyDigest.builder()
                .rawArticle(article)
                .digestDate(digestDate)
                .domain(parsed.domain())
                .tags(toJsonArray(parsed.tags()))
                .oneLiner(parsed.oneLiner())
                .problem(parsed.problem())
                .risk(parsed.risk())
                .impactTarget(parsed.impactTarget())
                .action(toJsonArray(parsed.actions()))
                .modelUsed(anthropicProperties.model())
                .build();

        return dailyDigestRepository.save(digest);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    private String callClaudeApi(RawArticle article) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", anthropicProperties.model());
        requestBody.put("max_tokens", anthropicProperties.maxTokens());
        requestBody.put("system", promptBuilder.systemPrompt());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode content = userMessage.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", promptBuilder.userMessage(article));

        WebClient webClient = WebClient.builder().build();
        String response = webClient.post()
                .uri(ANTHROPIC_API_URL)
                .header("x-api-key", anthropicProperties.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API 응답 파싱 실패: " + response, e);
        }
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
