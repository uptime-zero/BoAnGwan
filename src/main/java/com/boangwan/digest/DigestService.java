package com.boangwan.digest;

import com.boangwan.delivery.DigestDeliveryService;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.DeliveryLog;
import com.boangwan.domain.RawArticle;
import com.boangwan.repository.DailyDigestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigestService {

    private final DailyDigestRepository dailyDigestRepository;
    private final ArticleSelector articleSelector;
    private final DigestGenerator digestGenerator;
    private final DigestDeliveryService digestDeliveryService;

    public record DigestResult(long digestId, String articleTitle, String domain, String deliveryStatus) {}

    public DigestResult runFor(LocalDate date) {
        if (dailyDigestRepository.existsByDigestDate(date)) {
            log.info("{}에 이미 다이제스트가 생성되어 있습니다", date);
            DailyDigest existing = dailyDigestRepository.findTopByDigestDateOrderByIdDesc(date).orElseThrow();
            return new DigestResult(existing.getId(), existing.getRawArticle().getTitle(), existing.getDomain(), "ALREADY_EXISTS");
        }

        Optional<RawArticle> selected = articleSelector.select(date);
        if (selected.isEmpty()) {
            throw new IllegalStateException("선정 가능한 기사가 없습니다");
        }

        RawArticle article = selected.get();
        DailyDigest digest;
        try {
            digest = digestGenerator.generate(article, date);
            article.summarize();
        } catch (Exception e) {
            log.error("Claude 요약 실패 [{}]: {}", article.getTitle(), e.getMessage());
            article.fail();
            throw new IllegalStateException("Claude 요약 실패: " + e.getMessage(), e);
        }

        DeliveryLog deliveryLog;
        try {
            deliveryLog = digestDeliveryService.deliver(digest);
        } catch (Exception e) {
            log.error("Discord 발송 실패: {}", e.getMessage());
            return new DigestResult(digest.getId(), article.getTitle(), digest.getDomain(), "DELIVERY_FAILED");
        }

        return new DigestResult(digest.getId(), article.getTitle(), digest.getDomain(), deliveryLog.getStatus().name());
    }
}
