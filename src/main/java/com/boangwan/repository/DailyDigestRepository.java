package com.boangwan.repository;

import com.boangwan.domain.DailyDigest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DailyDigestRepository extends JpaRepository<DailyDigest, Long> {

    boolean existsByDigestDate(LocalDate digestDate);

    Optional<DailyDigest> findTopByDigestDateOrderByIdDesc(LocalDate digestDate);
}
