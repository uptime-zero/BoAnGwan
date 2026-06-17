package com.boangwan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "raw_article",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "guid"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false, length = 500)
    private String guid;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 1000)
    private String link;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleStatus status;

    @Builder
    public RawArticle(Source source, String guid, String title, String link,
                      String description, LocalDateTime publishedAt) {
        this.source = source;
        this.guid = guid;
        this.title = title;
        this.link = link;
        this.description = description;
        this.publishedAt = publishedAt;
        this.fetchedAt = LocalDateTime.now();
        this.status = ArticleStatus.COLLECTED;
    }

    public void select() {
        this.status = ArticleStatus.SELECTED;
    }

    public void summarize() {
        this.status = ArticleStatus.SUMMARIZED;
    }

    public void fail() {
        this.status = ArticleStatus.FAILED;
    }

    public void skip() {
        this.status = ArticleStatus.SKIPPED;
    }
}
