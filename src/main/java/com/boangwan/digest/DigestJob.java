package com.boangwan.digest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestJob {

    private final DigestService digestService;

    @Scheduled(cron = "${schedule.digest.cron}", zone = "${schedule.timezone}")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("DigestJob 시작 ({})", today);
        try {
            digestService.runFor(today);
            log.info("DigestJob 완료 ({})", today);
        } catch (Exception e) {
            log.error("DigestJob 오류", e);
        }
    }
}
