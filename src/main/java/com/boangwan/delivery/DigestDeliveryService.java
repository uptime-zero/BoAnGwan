package com.boangwan.delivery;

import com.boangwan.config.DiscordProperties;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.DeliveryChannel;
import com.boangwan.domain.DeliveryLog;
import com.boangwan.domain.DeliveryStatus;
import com.boangwan.repository.DeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestDeliveryService {

    private final DiscordProperties discordProperties;
    private final DiscordEmbedBuilder embedBuilder;
    private final DeliveryLogRepository deliveryLogRepository;

    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public DeliveryLog deliver(DailyDigest digest) {
        String payload = embedBuilder.build(digest);
        try {
            WebClient.builder().build()
                    .post()
                    .uri(discordProperties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            DeliveryLog log = DeliveryLog.builder()
                    .digest(digest)
                    .channel(DeliveryChannel.DISCORD)
                    .status(DeliveryStatus.SUCCESS)
                    .retryCount(0)
                    .sentAt(LocalDateTime.now())
                    .build();
            return deliveryLogRepository.save(log);
        } catch (Exception e) {
            log.error("Discord 발송 실패: {}", e.getMessage());
            DeliveryLog failLog = DeliveryLog.builder()
                    .digest(digest)
                    .channel(DeliveryChannel.DISCORD)
                    .status(DeliveryStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .retryCount(0)
                    .sentAt(LocalDateTime.now())
                    .build();
            deliveryLogRepository.save(failLog);
            throw e;
        }
    }
}
