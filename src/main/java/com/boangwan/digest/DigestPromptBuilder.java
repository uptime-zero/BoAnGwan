package com.boangwan.digest;

import com.boangwan.domain.RawArticle;
import org.springframework.stereotype.Component;

@Component
public class DigestPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            당신은 보안 기사를 읽고 핵심만 압축하는 요약 도구입니다.
            원문에 없는 내용을 추가하거나 추론하지 마세요.
            아래 JSON 형식으로만 응답하세요.

            {
              "one_liner": "한 문장 요약 (40자 이내)",
              "domain": "보안 도메인 (예: WEB_APP, CRYPTO, CLOUD, NETWORK 등)",
              "tags": ["키워드1", "키워드2", "키워드3"],
              "problem": "무슨 문제인가 — 원문 기반으로만 (200자 이내)",
              "risk": "왜 중요한가 — 원문 기반으로만 (200자 이내)",
              "impact_target": "영향 대상 제품/서비스/사용자 (100자 이내)",
              "action": ["대응 방안 1", "대응 방안 2", "대응 방안 3"]
            }

            원문에 대응 방안이 없으면 action은 빈 배열로 두세요.
            JSON 외 다른 출력 금지.""";

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userMessage(RawArticle article) {
        return """
                제목: %s

                내용: %s

                원문 URL: %s
                """.formatted(
                article.getTitle(),
                article.getDescription() != null ? article.getDescription() : "(본문 없음)",
                article.getLink()
        );
    }
}
