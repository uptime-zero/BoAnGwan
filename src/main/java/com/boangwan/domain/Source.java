package com.boangwan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "rss_url", nullable = false, length = 500)
    private String rssUrl;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 20)
    private String encoding;

    @Enumerated(EnumType.STRING)
    @Column(name = "guid_type", nullable = false, length = 20)
    private GuidType guidType;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source_type", nullable = false, length = 20)
    private ContentSourceType contentSourceType;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Source(String name, String rssUrl, String language, String encoding,
                  GuidType guidType, ContentSourceType contentSourceType, int priority, boolean active) {
        this.name = name;
        this.rssUrl = rssUrl;
        this.language = language;
        this.encoding = encoding;
        this.guidType = guidType;
        this.contentSourceType = contentSourceType;
        this.priority = priority;
        this.active = active;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }
}
