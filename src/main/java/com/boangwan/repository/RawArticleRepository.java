package com.boangwan.repository;

import com.boangwan.domain.RawArticle;
import com.boangwan.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface RawArticleRepository extends JpaRepository<RawArticle, Long> {

    boolean existsBySourceAndGuid(Source source, String guid);

    @Query("""
            SELECT a FROM RawArticle a
            JOIN FETCH a.source s
            WHERE s.active = true
              AND (a.status = 'COLLECTED'
                   OR (a.status = 'FAILED' AND a.attemptCount < :maxAttempts))
              AND COALESCE(a.publishedAt, a.fetchedAt) >= :since
              AND NOT EXISTS (SELECT d.id FROM DailyDigest d WHERE d.rawArticle = a)
            ORDER BY COALESCE(a.publishedAt, a.fetchedAt) DESC, s.priority ASC, a.id DESC
            """)
    List<RawArticle> findCandidates(@Param("since") LocalDateTime since,
                                    @Param("maxAttempts") int maxAttempts);
}
