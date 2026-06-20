package com.boangwan.delivery;

import com.boangwan.domain.DailyDigest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class DiscordEmbedBuilder {

    private static final Map<String, Integer> DOMAIN_COLORS = Map.ofEntries(
            Map.entry("WEB_APP", 0xE74C3C),
            Map.entry("CRYPTO", 0x9B59B6),
            Map.entry("CLOUD", 0x3498DB),
            Map.entry("NETWORK", 0x2ECC71),
            Map.entry("MALWARE", 0xE67E22),
            Map.entry("SOCIAL_ENGINEERING", 0xF39C12),
            Map.entry("SUPPLY_CHAIN", 0x1ABC9C),
            Map.entry("IOT", 0x27AE60),
            Map.entry("MOBILE", 0x8E44AD)
    );
    private static final int DEFAULT_COLOR = 0x95A5A6;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(DailyDigest digest) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode embeds = root.putArray("embeds");
            ObjectNode embed = embeds.addObject();

            embed.put("title", digest.getOneLiner());
            embed.put("color", DOMAIN_COLORS.getOrDefault(digest.getDomain(), DEFAULT_COLOR));
            embed.put("url", digest.getRawArticle().getLink());

            ArrayNode fields = embed.putArray("fields");
            addField(fields, "📋 핵심 내용", digest.getProblem(), false);
            addField(fields, "⚠️ 왜 중요한가", digest.getRisk(), false);
            addField(fields, "🎯 영향 대상", digest.getImpactTarget(), false);

            String actionsText = formatActions(digest.getAction());
            if (!actionsText.isBlank()) {
                addField(fields, "✅ 대응 방안", actionsText, false);
            }
            addField(fields, "🔗 원문", digest.getRawArticle().getLink(), false);

            String footerText = buildFooter(digest);
            embed.putObject("footer").put("text", footerText);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Discord Embed 생성 실패", e);
        }
    }

    private void addField(ArrayNode fields, String name, String value, boolean inline) {
        if (value == null || value.isBlank()) return;
        ObjectNode field = fields.addObject();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
    }

    private String formatActions(String actionJson) {
        if (actionJson == null || actionJson.isBlank()) return "";
        try {
            List<String> actions = objectMapper.readerForListOf(String.class).readValue(actionJson);
            if (actions.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < actions.size(); i++) {
                sb.append(i + 1).append(". ").append(actions.get(i));
                if (i < actions.size() - 1) sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return actionJson;
        }
    }

    private String buildFooter(DailyDigest digest) {
        StringBuilder footer = new StringBuilder();
        if (digest.getDomain() != null) footer.append(digest.getDomain());
        String tagsText = formatTags(digest.getTags());
        if (!tagsText.isBlank()) {
            footer.append(" | ").append(tagsText);
        }
        if (digest.getModelUsed() != null) {
            footer.append(" | ").append(digest.getModelUsed());
        }
        return footer.toString();
    }

    private String formatTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return "";
        try {
            List<String> tags = objectMapper.readerForListOf(String.class).readValue(tagsJson);
            return String.join(", ", tags);
        } catch (Exception e) {
            return "";
        }
    }
}
