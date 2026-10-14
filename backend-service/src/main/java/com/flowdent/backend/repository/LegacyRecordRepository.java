package com.flowdent.backend.repository;

import com.flowdent.backend.model.LegacyRecord;
import com.flowdent.backend.model.LegacyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LegacyRecordRepository extends JpaRepository<LegacyRecord, Long> {
    List<LegacyRecord> findByTypeIn(Collection<LegacyType> types);
    List<LegacyRecord> findAllByOrderByRecordDateDescIdDesc();
    boolean existsByClientId(Long clientId);
    boolean existsBySupplierId(Long supplierId);

    @Modifying
    @Query("delete from LegacyRecord r where r.batch.id = :batchId")
    int deleteByBatchId(@Param("batchId") Long batchId);
}
