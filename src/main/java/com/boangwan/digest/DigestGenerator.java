package com.boangwan.digest;

import com.boangwan.config.AnthropicProperties;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.RawArticle;
import com.boangwan.repository.DailyDigestRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestGenerator {

    private final AnthropicProperties anthropicProperties;
    private final AnthropicClient anthropicClient;
    private final DigestPromptBuilder promptBuilder;
    private final DigestParser digestParser;
    private final DailyDigestRepository dailyDigestRepository;
    private final ObjectMapper objectMapper;

    public DailyDigest generate(RawArticle article, LocalDate digestDate) {
        String responseText = anthropicClient.call(
                promptBuilder.systemPrompt(),
                promptBuilder.userMessage(article)
        );

        DigestParser.ParsedDigest parsed = digestParser.parse(responseText);
        if (!parsed.isSufficient()) {
            throw new IllegalStateException("Claude 응답 품질 미달 (one_liner 또는 problem/risk 비어 있음)");
        }

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

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
