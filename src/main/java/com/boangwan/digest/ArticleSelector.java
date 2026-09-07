package com.boangwan.digest;

import com.boangwan.config.SelectionProperties;
import com.boangwan.domain.RawArticle;
import com.boangwan.repository.RawArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSelector {

    private final RawArticleRepository rawArticleRepository;
    private final SelectionProperties selectionProperties;

    @Transactional
    public Optional<RawArticle> select(LocalDate today) {
        LocalDateTime since = LocalDateTime.now()
                .minusHours(selectionProperties.candidateWindowHours());
        List<RawArticle> candidates = rawArticleRepository.findCandidates(
                since, selectionProperties.maxFailedAttempts());

        if (candidates.isEmpty()) {
            log.warn("선정할 기사가 없습니다 ({})", today);
            return Optional.empty();
        }
        RawArticle selected = candidates.getFirst();
        selected.select();
        log.info("기사 선정: [{}] {} (시도 {}회)",
                selected.getSource().getName(), selected.getTitle(), selected.getAttemptCount());
        return Optional.of(selected);
    }
}
