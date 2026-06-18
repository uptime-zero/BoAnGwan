package com.boangwan.digest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class DigestParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedDigest parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String oneLiner = textOrEmpty(root, "one_liner");
            String domain = textOrEmpty(root, "domain");
            String problem = textOrEmpty(root, "problem");
            String risk = textOrEmpty(root, "risk");
            String impactTarget = textOrEmpty(root, "impact_target");
            List<String> tags = toStringList(root.get("tags"));
            List<String> actions = toStringList(root.get("action"));
            return new ParsedDigest(oneLiner, domain, tags, problem, risk, impactTarget, actions);
        } catch (Exception e) {
            throw new IllegalArgumentException("Claude 응답 JSON 파싱 실패: " + json, e);
        }
    }

    private String textOrEmpty(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && !node.isNull() ? node.asText() : "";
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    public record ParsedDigest(
            String oneLiner,
            String domain,
            List<String> tags,
            String problem,
            String risk,
            String impactTarget,
            List<String> actions
    ) {}
}
