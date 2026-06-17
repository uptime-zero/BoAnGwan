package com.boangwan.repository;

import com.boangwan.domain.RawArticle;
import com.boangwan.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface RawArticleRepository extends JpaRepository<RawArticle, Long> {

    boolean existsBySourceAndGuid(Source source, String guid);

    @Query("""
            SELECT a FROM RawArticle a
            JOIN FETCH a.source s
            WHERE a.status IN ('COLLECTED', 'FAILED')
              AND a.id NOT IN (
                  SELECT d.rawArticle.id FROM DailyDigest d WHERE d.digestDate = :today
              )
            ORDER BY s.priority ASC, a.publishedAt DESC
            """)
    List<RawArticle> findCandidates(LocalDate today);
}
