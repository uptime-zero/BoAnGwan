package com.boangwan.collector;

import com.boangwan.domain.Source;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.StringReader;
import java.nio.charset.Charset;

@Component
public class RssFetcher {

    private final WebClient rssWebClient;

    public RssFetcher(WebClient rssWebClient) {
        this.rssWebClient = rssWebClient;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public SyndFeed fetch(Source source) {
        byte[] bytes = rssWebClient.get()
                .uri(source.getRssUrl())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (bytes == null) {
            throw new IllegalStateException("RSS 응답이 비어 있습니다: " + source.getRssUrl());
        }

        String xml = new String(bytes, Charset.forName(source.getEncoding()));
        try {
            SyndFeedInput input = new SyndFeedInput();
            input.setAllowDoctypes(true);
            return input.build(new StringReader(xml));
        } catch (Exception e) {
            throw new RuntimeException("RSS 파싱 실패: " + source.getRssUrl(), e);
        }
    }
}
