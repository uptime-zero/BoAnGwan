package com.boangwan.admin;

import com.boangwan.collector.CollectService;
import com.boangwan.collector.CollectService.SourceCollectResult;
import com.boangwan.delivery.DigestDeliveryService;
import com.boangwan.digest.DigestService;
import com.boangwan.digest.DigestService.DigestResult;
import com.boangwan.domain.DailyDigest;
import com.boangwan.domain.DeliveryLog;
import com.boangwan.repository.DailyDigestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CollectService collectService;
    private final DigestService digestService;
    private final DigestDeliveryService digestDeliveryService;
    private final DailyDigestRepository dailyDigestRepository;

    @PostMapping("/collect")
    public ResponseEntity<CollectResponse> collect() {
        List<SourceCollectResult> results = collectService.collectAll();
        int total = results.stream().mapToInt(SourceCollectResult::collected).sum();
        return ResponseEntity.ok(new CollectResponse(results.size(), total, results));
    }

    @PostMapping("/collect/{sourceId}")
    public ResponseEntity<CollectResponse> collectOne(@PathVariable long sourceId) {
        SourceCollectResult result = collectService.collectOne(sourceId);
        return ResponseEntity.ok(new CollectResponse(1, result.collected(), List.of(result)));
    }

    @PostMapping("/digest/trigger")
    public ResponseEntity<DigestResult> triggerDigest() {
        DigestResult result = digestService.runFor(LocalDate.now());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/deliver/{digestId}")
    public ResponseEntity<DeliverResponse> deliver(@PathVariable long digestId) {
        DailyDigest digest = dailyDigestRepository.findById(digestId)
                .orElseThrow(() -> new IllegalArgumentException("다이제스트를 찾을 수 없습니다: " + digestId));
        try {
            DeliveryLog log = digestDeliveryService.deliver(digest);
            return ResponseEntity.ok(new DeliverResponse(digestId, log.getStatus().name(), null));
        } catch (Exception e) {
            return ResponseEntity.ok(new DeliverResponse(digestId, "FAILED", e.getMessage()));
        }
    }

    public record CollectResponse(int sourceCount, int totalCollected, List<SourceCollectResult> results) {}

    public record DeliverResponse(long digestId, String deliveryStatus, String errorMessage) {}
}
