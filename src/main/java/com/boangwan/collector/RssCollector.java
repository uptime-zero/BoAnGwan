package com.boangwan.collector;

import com.boangwan.domain.RawArticle;
import com.boangwan.domain.Source;
import com.boangwan.repository.RawArticleRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssCollector {

    private final RssFetcher rssFetcher;
    private final GuidExtractor guidExtractor;
    private final RawArticleRepository rawArticleRepository;

    @Transactional
    public int collect(Source source) {
        SyndFeed feed;
        try {
            feed = rssFetcher.fetch(source);
        } catch (Exception e) {
            log.warn("RSS 수집 실패 [{}]: {}", source.getName(), e.getMessage());
            return 0;
        }

        int saved = 0;
        for (SyndEntry entry : feed.getEntries()) {
            String guid = guidExtractor.extract(entry, source.getGuidType());
            if (guid == null) continue;

            if (rawArticleRepository.existsBySourceAndGuid(source, guid)) continue;

            String description = extractDescription(entry);
            LocalDateTime publishedAt = toLocalDateTime(entry.getPublishedDate());

            try {
                rawArticleRepository.save(RawArticle.builder()
                        .source(source)
                        .guid(guid)
                        .title(entry.getTitle() != null ? entry.getTitle() : "제목 없음")
                        .link(entry.getLink())
                        .description(description)
                        .publishedAt(publishedAt)
                        .build());
                saved++;
            } catch (DataIntegrityViolationException e) {
                // 동시 수집 시 중복 — 무시
                log.debug("중복 기사 무시 [{}] guid={}", source.getName(), guid);
            }
        }
        log.info("RSS 수집 완료 [{}] 신규 {}건", source.getName(), saved);
        return saved;
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() != null) {
            return entry.getDescription().getValue();
        }
        if (!entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime();
    }
}
