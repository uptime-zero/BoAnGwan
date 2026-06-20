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
    private DigestService digestService;

    @Test
    void 오늘_이미_다이제스트_있으면_건너뜀() {
        DailyDigest existing = mock(DailyDigest.class);
        RawArticle article = mock(RawArticle.class);
        when(existing.getId()).thenReturn(1L);
        when(existing.getDomain()).thenReturn("WEB_APP");
        when(existing.getRawArticle()).thenReturn(article);
        when(article.getTitle()).thenReturn("기존 기사");
        when(dailyDigestRepository.existsByDigestDate(any(LocalDate.class))).thenReturn(true);
        when(dailyDigestRepository.findTopByDigestDateOrderByIdDesc(any())).thenReturn(Optional.of(existing));

        digestService.runFor(LocalDate.now());

        verify(articleSelector, never()).select(any());
        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 선정_기사_없으면_종료() {
        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.empty());

        try {
            digestService.runFor(LocalDate.now());
        } catch (IllegalStateException ignored) {}

        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 정상_흐름_전체_단계_호출() {
        RawArticle article = mock(RawArticle.class);
        DailyDigest digest = mock(DailyDigest.class);
        DeliveryLog deliveryLog = mock(DeliveryLog.class);
        when(digest.getId()).thenReturn(1L);
        when(digest.getDomain()).thenReturn("WEB_APP");
        when(article.getTitle()).thenReturn("테스트 기사");
        when(deliveryLog.getStatus()).thenReturn(DeliveryStatus.SUCCESS);

        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.of(article));
        when(digestGenerator.generate(any(), any())).thenReturn(digest);
        when(digestDeliveryService.deliver(any())).thenReturn(deliveryLog);

        digestService.runFor(LocalDate.now());

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

        try {
            digestService.runFor(LocalDate.now());
        } catch (IllegalStateException ignored) {}

        verify(article).fail();
        verify(digestDeliveryService, never()).deliver(any());
    }
}
