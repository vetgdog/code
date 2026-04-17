package com.code.repository;

import com.code.entity.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {
    Optional<SalesRecord> findBySalesOrderId(Long orderId);
    List<SalesRecord> findAllByOrderByCreatedAtDesc();
    List<SalesRecord> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    List<SalesRecord> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime startTime);
    List<SalesRecord> findByCreatedAtLessThanEqualOrderByCreatedAtDesc(LocalDateTime endTime);
    List<SalesRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);
}


