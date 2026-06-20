package com.boangwan.digest;

import com.boangwan.delivery.DigestDeliveryService;
import com.boangwan.domain.*;
import com.boangwan.repository.DailyDigestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestJobTest {

    @Mock private DailyDigestRepository dailyDigestRepository;
    @Mock private ArticleSelector articleSelector;
    @Mock private DigestGenerator digestGenerator;
    @Mock private DigestDeliveryService digestDeliveryService;

    @InjectMocks
    private DigestJob digestJob;

    @Test
    void 오늘_이미_다이제스트_있으면_건너뜀() {
        when(dailyDigestRepository.existsByDigestDate(any(LocalDate.class))).thenReturn(true);

        digestJob.run();

        verify(articleSelector, never()).select(any());
        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 선정_기사_없으면_종료() {
        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.empty());

        digestJob.run();

        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 정상_흐름_전체_단계_호출() {
        RawArticle article = mock(RawArticle.class);
        DailyDigest digest = mock(DailyDigest.class);
        DeliveryLog deliveryLog = mock(DeliveryLog.class);

        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.of(article));
        when(digestGenerator.generate(any(), any())).thenReturn(digest);
        when(digestDeliveryService.deliver(any())).thenReturn(deliveryLog);

        digestJob.run();

        verify(articleSelector).select(any());
        verify(digestGenerator).generate(eq(article), any());
        verify(digestDeliveryService).deliver(eq(digest));
        verify(article).summarize();
    }

    @Test
    void Claude_요약_실패시_article_fail_처리() {
        RawArticle article = mock(RawArticle.class);

        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.of(article));
        when(digestGenerator.generate(any(), any())).thenThrow(new RuntimeException("API 오류"));

        digestJob.run();

        verify(article).fail();
        verify(digestDeliveryService, never()).deliver(any());
    }
}
