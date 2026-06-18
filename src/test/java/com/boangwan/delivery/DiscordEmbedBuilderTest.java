package com.boangwan.delivery;

import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.RawArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordEmbedBuilderTest {

    private final DiscordEmbedBuilder builder = new DiscordEmbedBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embed_필수_필드_포함() throws Exception {
        RawArticle article = mock(RawArticle.class);
        when(article.getLink()).thenReturn("https://example.com/article");

        DailyDigest digest = mock(DailyDigest.class);
        when(digest.getOneLiner()).thenReturn("Log4Shell 취약점 발견");
        when(digest.getDomain()).thenReturn("WEB_APP");
        when(digest.getProblem()).thenReturn("원격 코드 실행 가능");
        when(digest.getRisk()).thenReturn("치명적 영향");
        when(digest.getImpactTarget()).thenReturn("Log4j 사용 시스템");
        when(digest.getAction()).thenReturn("[\"업그레이드\"]");
        when(digest.getTags()).thenReturn("[\"Log4j\",\"RCE\"]");
        when(digest.getModelUsed()).thenReturn("claude-haiku-4-5");
        when(digest.getRawArticle()).thenReturn(article);

        String payload = builder.build(digest);
        JsonNode root = objectMapper.readTree(payload);
        JsonNode embed = root.get("embeds").get(0);

        assertThat(embed.get("title").asText()).isEqualTo("Log4Shell 취약점 발견");
        assertThat(embed.get("color").asInt()).isEqualTo(0xE74C3C);
        assertThat(embed.get("footer").get("text").asText()).contains("WEB_APP");
        assertThat(embed.get("footer").get("text").asText()).contains("claude-haiku-4-5");
    }

    @Test
    void 알_수_없는_도메인은_기본_색상() throws Exception {
        RawArticle article = mock(RawArticle.class);
        when(article.getLink()).thenReturn("https://example.com");

        DailyDigest digest = mock(DailyDigest.class);
        when(digest.getOneLiner()).thenReturn("제목");
        when(digest.getDomain()).thenReturn("UNKNOWN_DOMAIN");
        when(digest.getProblem()).thenReturn("문제");
        when(digest.getRisk()).thenReturn("위험");
        when(digest.getImpactTarget()).thenReturn("대상");
        when(digest.getAction()).thenReturn("[]");
        when(digest.getTags()).thenReturn("[]");
        when(digest.getModelUsed()).thenReturn("claude-haiku-4-5");
        when(digest.getRawArticle()).thenReturn(article);

        String payload = builder.build(digest);
        JsonNode embed = objectMapper.readTree(payload).get("embeds").get(0);

        assertThat(embed.get("color").asInt()).isEqualTo(0x95A5A6);
    }
}
