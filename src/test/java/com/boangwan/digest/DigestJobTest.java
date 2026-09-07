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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestJobTest {

    @Mock private DailyDigestRepository dailyDigestRepository;
    @Mock private ArticleSelector articleSelector;
    @Mock private DigestGenerator digestGenerator;
    @Mock private DigestDeliveryService digestDeliveryService;
    @Mock private ArticleStatusService articleStatusService;

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

        DigestService.DigestResult result = digestService.runFor(LocalDate.now());

        assertThat(result.deliveryStatus()).isEqualTo("ALREADY_EXISTS");
        verify(articleSelector, never()).select(any());
        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 선정_기사_없으면_NO_CANDIDATES_반환() {
        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.empty());

        DigestService.DigestResult result = digestService.runFor(LocalDate.now());

        assertThat(result.deliveryStatus()).isEqualTo("NO_CANDIDATES");
        assertThat(result.digestId()).isNull();
        verify(digestGenerator, never()).generate(any(), any());
        verify(digestDeliveryService, never()).deliver(any());
    }

    @Test
    void 정상_흐름_전체_단계_호출() {
        RawArticle article = mock(RawArticle.class);
        DailyDigest digest = mock(DailyDigest.class);
        DeliveryLog deliveryLog = mock(DeliveryLog.class);
        when(article.getId()).thenReturn(1L);
        when(article.getTitle()).thenReturn("테스트 기사");
        when(digest.getId()).thenReturn(1L);
        when(digest.getDomain()).thenReturn("WEB_APP");
        when(deliveryLog.getStatus()).thenReturn(DeliveryStatus.SUCCESS);

        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.of(article));
        when(digestGenerator.generate(any(), any())).thenReturn(digest);
        when(digestDeliveryService.deliver(any())).thenReturn(deliveryLog);

        DigestService.DigestResult result = digestService.runFor(LocalDate.now());

        assertThat(result.deliveryStatus()).isEqualTo("SUCCESS");
        verify(articleSelector).select(any());
        verify(digestGenerator).generate(eq(article), any());
        verify(digestDeliveryService).deliver(eq(digest));
        verify(articleStatusService).markSummarized(1L);
    }

    @Test
    void Claude_요약_실패시_markFailed_호출() {
        RawArticle article = mock(RawArticle.class);
        when(article.getId()).thenReturn(1L);
        when(article.getTitle()).thenReturn("테스트 기사");

        when(dailyDigestRepository.existsByDigestDate(any())).thenReturn(false);
        when(articleSelector.select(any())).thenReturn(Optional.of(article));
        when(digestGenerator.generate(any(), any())).thenThrow(new RuntimeException("API 오류"));

        assertThatThrownBy(() -> digestService.runFor(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Claude 요약 실패");

        verify(articleStatusService).markFailed(1L);
        verify(articleStatusService, never()).markSummarized(any());
        verify(digestDeliveryService, never()).deliver(any());
    }
}
