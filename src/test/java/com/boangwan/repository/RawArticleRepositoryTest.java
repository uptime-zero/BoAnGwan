package com.boangwan.repository;

import com.boangwan.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class RawArticleRepositoryTest {

    @Autowired
    private RawArticleRepository rawArticleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private DailyDigestRepository dailyDigestRepository;

    @Test
    void existsBySourceAndGuid_중복기사_감지() {
        Source source = sourceRepository.save(Source.builder()
                .name("보안뉴스")
                .rssUrl("https://example.com/rss")
                .language("ko")
                .encoding("UTF-8")
                .guidType(GuidType.LINK_IDX)
                .contentSourceType(ContentSourceType.RSS_ONLY)
                .priority(1)
                .active(true)
                .build());

        rawArticleRepository.save(RawArticle.builder()
                .source(source)
                .guid("12345")
                .title("테스트 기사")
                .link("https://example.com/12345")
                .description("내용")
                .publishedAt(LocalDateTime.now())
                .build());

        assertThat(rawArticleRepository.existsBySourceAndGuid(source, "12345")).isTrue();
        assertThat(rawArticleRepository.existsBySourceAndGuid(source, "99999")).isFalse();
    }

    @Test
    void findCandidates_COLLECTED_FAILED_기사만_반환() {
        Source source = sourceRepository.save(Source.builder()
                .name("테스트소스")
                .rssUrl("https://example.com/rss")
                .language("ko")
                .encoding("UTF-8")
                .guidType(GuidType.GUID_TAG)
                .contentSourceType(ContentSourceType.RSS_ONLY)
                .priority(10)
                .active(true)
                .build());

        RawArticle collected = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("수집됨").link("https://a.com/1")
                .publishedAt(LocalDateTime.now()).build());

        RawArticle summarized = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("2").title("요약됨").link("https://a.com/2")
                .publishedAt(LocalDateTime.now()).build());
        summarized.summarize();
        rawArticleRepository.save(summarized);

        List<RawArticle> candidates = rawArticleRepository.findCandidates(LocalDate.now());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getGuid()).isEqualTo("1");
    }
}
