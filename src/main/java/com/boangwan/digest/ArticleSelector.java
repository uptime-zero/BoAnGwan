package com.boangwan.digest;

import com.boangwan.domain.RawArticle;
import com.boangwan.repository.RawArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSelector {

    private final RawArticleRepository rawArticleRepository;

    @Transactional
    public Optional<RawArticle> select(LocalDate today) {
        List<RawArticle> candidates = rawArticleRepository.findCandidates(today);
        if (candidates.isEmpty()) {
            log.warn("선정할 기사가 없습니다 ({})", today);
            return Optional.empty();
        }
        RawArticle selected = candidates.get(0);
        selected.select();
        log.info("기사 선정: [{}] {}", selected.getSource().getName(), selected.getTitle());
        return Optional.of(selected);
    }
}
