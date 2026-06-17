package com.boangwan.digest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestParserTest {

    private final DigestParser parser = new DigestParser();

    @Test
    void 정상_JSON_파싱() {
        String json = """
                {
                  "one_liner": "Log4Shell 취약점 발견",
                  "domain": "WEB_APP",
                  "tags": ["Log4j", "RCE", "CVE-2021-44228"],
                  "problem": "Apache Log4j에서 원격 코드 실행 취약점 발견",
                  "risk": "공격자가 임의 코드 실행 가능",
                  "impact_target": "Log4j 2.x 사용 시스템",
                  "action": ["Log4j 2.17.1 이상 업그레이드", "WAF 규칙 적용"]
                }
                """;

        DigestParser.ParsedDigest result = parser.parse(json);

        assertThat(result.oneLiner()).isEqualTo("Log4Shell 취약점 발견");
        assertThat(result.domain()).isEqualTo("WEB_APP");
        assertThat(result.tags()).containsExactly("Log4j", "RCE", "CVE-2021-44228");
        assertThat(result.actions()).containsExactly("Log4j 2.17.1 이상 업그레이드", "WAF 규칙 적용");
    }

    @Test
    void action_빈_배열_처리() {
        String json = """
                {
                  "one_liner": "요약",
                  "domain": "NETWORK",
                  "tags": [],
                  "problem": "문제",
                  "risk": "위험",
                  "impact_target": "대상",
                  "action": []
                }
                """;

        DigestParser.ParsedDigest result = parser.parse(json);

        assertThat(result.actions()).isEmpty();
    }

    @Test
    void 잘못된_JSON_예외_발생() {
        assertThatThrownBy(() -> parser.parse("invalid json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 파싱 실패");
    }
}
