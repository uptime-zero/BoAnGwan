package com.boangwan.collector;

import com.boangwan.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectJob {

    private final SourceRepository sourceRepository;
    private final RssCollector rssCollector;

    @Scheduled(cron = "${schedule.collect.cron}", zone = "${schedule.timezone}")
    public void run() {
        log.info("RSS 수집 시작");
        try {
            sourceRepository.findAllByActiveTrue()
                    .forEach(rssCollector::collect);
        } catch (Exception e) {
            log.error("RSS 수집 잡 오류", e);
        }
        log.info("RSS 수집 완료");
    }
}
