package com.boangwan.digest;

import com.boangwan.delivery.DigestDeliveryService;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.RawArticle;
import com.boangwan.repository.DailyDigestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestJob {

    private final DailyDigestRepository dailyDigestRepository;
    private final ArticleSelector articleSelector;
    private final DigestGenerator digestGenerator;
    private final DigestDeliveryService digestDeliveryService;

    @Scheduled(cron = "${schedule.digest.cron}", zone = "${schedule.timezone}")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("DigestJob 시작 ({})", today);
        try {
            if (dailyDigestRepository.existsByDigestDate(today)) {
                log.info("오늘({}) 이미 다이제스트 생성됨 — 건너뜀", today);
                return;
            }

            Optional<RawArticle> selected = articleSelector.select(today);
            if (selected.isEmpty()) {
                log.warn("선정된 기사 없음 — DigestJob 종료");
                return;
            }

            RawArticle article = selected.get();
            DailyDigest digest;
            try {
                digest = digestGenerator.generate(article, today);
                article.summarize();
            } catch (Exception e) {
                log.error("Claude 요약 실패 [{}]: {}", article.getTitle(), e.getMessage());
                article.fail();
                return;
            }

            try {
                digestDeliveryService.deliver(digest);
                log.info("DigestJob 완료 — 기사: {}", article.getTitle());
            } catch (Exception e) {
                log.error("Discord 발송 실패: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("DigestJob 예기치 않은 오류", e);
        }
    }
}
