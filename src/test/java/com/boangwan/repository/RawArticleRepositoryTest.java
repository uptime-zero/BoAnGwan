package com.boangwan.repository;

import com.boangwan.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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

    private Source source;
    private static final LocalDateTime WITHIN_WINDOW = LocalDateTime.now().minusHours(10);
    private static final LocalDateTime OUTSIDE_WINDOW = LocalDateTime.now().minusHours(72);

    @BeforeEach
    void setUp() {
        source = sourceRepository.save(Source.builder()
                .name("테스트소스")
                .rssUrl("https://example.com/rss")
                .language("ko")
                .encoding("UTF-8")
                .guidType(GuidType.GUID_TAG)
                .contentSourceType(ContentSourceType.RSS_ONLY)
                .priority(10)
                .active(true)
                .build());
    }

    @Test
    void existsBySourceAndGuid_중복기사_감지() {
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
        RawArticle collected = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("수집됨").link("https://a.com/1")
                .publishedAt(WITHIN_WINDOW).build());

        RawArticle summarized = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("2").title("요약됨").link("https://a.com/2")
                .publishedAt(WITHIN_WINDOW).build());
        summarized.summarize();
        rawArticleRepository.save(summarized);

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getGuid()).isEqualTo("1");
    }

    @Test
    void findCandidates_attemptCount_상한_초과_FAILED_제외() {
        RawArticle failed = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("FAILED 기사").link("https://a.com/1")
                .publishedAt(WITHIN_WINDOW).build());
        // attempt_count를 2로 만들기 위해 select() 2회 호출
        failed.select();
        failed.select();
        failed.fail();
        rawArticleRepository.save(failed);

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findCandidates_attemptCount_1_FAILED_포함() {
        RawArticle failed = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("FAILED 기사").link("https://a.com/1")
                .publishedAt(WITHIN_WINDOW).build());
        failed.select();
        failed.fail();
        rawArticleRepository.save(failed);

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).hasSize(1);
    }

    @Test
    void findCandidates_48시간_초과_기사_제외() {
        rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("오래된 기사").link("https://a.com/1")
                .publishedAt(OUTSIDE_WINDOW).build());

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findCandidates_publishedAt_null_fetchedAt_최근_포함() {
        rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("날짜없는 기사").link("https://a.com/1")
                .publishedAt(null).build());  // fetchedAt은 생성자에서 now()로 설정됨

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getGuid()).isEqualTo("1");
    }

    @Test
    void findCandidates_이미_DailyDigest_있는_기사_제외() {
        RawArticle article = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("이미 소화된 기사").link("https://a.com/1")
                .publishedAt(WITHIN_WINDOW).build());
        article.select();
        rawArticleRepository.save(article);

        dailyDigestRepository.save(DailyDigest.builder()
                .rawArticle(article)
                .digestDate(LocalDate.now().minusDays(3))
                .domain("WEB_APP")
                .tags("[]")
                .oneLiner("요약")
                .problem("문제")
                .risk("위험")
                .impactTarget("대상")
                .action("[]")
                .modelUsed("claude-haiku-4-5")
                .build());

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findCandidates_최신성_우선_정렬() {
        RawArticle older = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("1").title("오래된 기사").link("https://a.com/1")
                .publishedAt(LocalDateTime.now().minusHours(24)).build());

        RawArticle newer = rawArticleRepository.save(RawArticle.builder()
                .source(source).guid("2").title("최신 기사").link("https://a.com/2")
                .publishedAt(LocalDateTime.now().minusHours(1)).build());

        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                LocalDateTime.now().minusHours(48), 2);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).getGuid()).isEqualTo("2");
    }
}
