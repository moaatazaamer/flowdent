package com.flowdent.backend.repository;

import com.flowdent.backend.model.LegacyBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegacyBatchRepository extends JpaRepository<LegacyBatch, Long> {
    Optional<LegacyBatch> findFirstByFileHash(String fileHash);
    List<LegacyBatch> findAllByOrderByImportedAtDescIdDesc();
}
