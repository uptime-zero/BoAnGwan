package com.boangwan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "digest_id", nullable = false)
    private DailyDigest digest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    public DeliveryLog(DailyDigest digest, DeliveryChannel channel, DeliveryStatus status,
                       String errorMessage, int retryCount, LocalDateTime sentAt) {
        this.digest = digest;
        this.channel = channel;
        this.status = status;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.sentAt = sentAt;
    }

    public void markRetrying(String errorMessage) {
        this.status = DeliveryStatus.RETRYING;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    public void markFailed(String errorMessage) {
        this.status = DeliveryStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
