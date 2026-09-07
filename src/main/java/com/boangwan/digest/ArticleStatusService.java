package com.boangwan.digest;

import com.boangwan.domain.RawArticle;
import com.boangwan.repository.RawArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStatusService {

    private final RawArticleRepository rawArticleRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSummarized(Long articleId) {
        RawArticle article = rawArticleRepository.findById(articleId).orElseThrow();
        article.summarize();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long articleId) {
        RawArticle article = rawArticleRepository.findById(articleId).orElseThrow();
        article.fail();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSkipped(Long articleId, String reason) {
        RawArticle article = rawArticleRepository.findById(articleId).orElseThrow();
        article.skip(reason);
        log.info("기사 SKIPPED [{}] 이유: {}", article.getTitle(), reason);
    }
}
