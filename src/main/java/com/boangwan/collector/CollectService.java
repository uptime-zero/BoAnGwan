package com.boangwan.collector;

import com.boangwan.domain.Source;
import com.boangwan.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private final SourceRepository sourceRepository;
    private final RssCollector rssCollector;

    public record SourceCollectResult(long sourceId, String sourceName, int collected) {}

    public List<SourceCollectResult> collectAll() {
        return sourceRepository.findAllByActiveTrue().stream()
                .map(source -> new SourceCollectResult(
                        source.getId(),
                        source.getName(),
                        rssCollector.collect(source)))
                .toList();
    }

    public SourceCollectResult collectOne(long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("소스를 찾을 수 없습니다: " + sourceId));
        return new SourceCollectResult(source.getId(), source.getName(), rssCollector.collect(source));
    }
}
