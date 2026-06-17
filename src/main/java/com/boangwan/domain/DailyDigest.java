package com.boangwan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_digest")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_article_id", nullable = false)
    private RawArticle rawArticle;

    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @Column(length = 50)
    private String domain;

    @Column(columnDefinition = "JSON")
    private String tags;

    @Column(name = "one_liner", length = 200)
    private String oneLiner;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(columnDefinition = "TEXT")
    private String risk;

    @Column(name = "impact_target", length = 200)
    private String impactTarget;

    @Column(columnDefinition = "JSON")
    private String action;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Builder
    public DailyDigest(RawArticle rawArticle, LocalDate digestDate, String domain,
                       String tags, String oneLiner, String problem, String risk,
                       String impactTarget, String action, String modelUsed,
                       Integer inputTokens, Integer outputTokens) {
        this.rawArticle = rawArticle;
        this.digestDate = digestDate;
        this.domain = domain;
        this.tags = tags;
        this.oneLiner = oneLiner;
        this.problem = problem;
        this.risk = risk;
        this.impactTarget = impactTarget;
        this.action = action;
        this.modelUsed = modelUsed;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.generatedAt = LocalDateTime.now();
    }
}
