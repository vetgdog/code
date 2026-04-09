package com.code.repository;

import com.code.entity.QualityRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QualityRecordRepository extends JpaRepository<QualityRecord, Long> {
}

