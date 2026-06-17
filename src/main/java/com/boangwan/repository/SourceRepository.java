package com.boangwan.repository;

import com.boangwan.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {
    List<Source> findAllByActiveTrue();
}
