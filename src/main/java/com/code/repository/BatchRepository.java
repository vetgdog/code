package com.code.repository;

import com.code.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
	Optional<Batch> findByBatchNoIgnoreCase(String batchNo);
	List<Batch> findAllByOrderByCreatedAtDesc();
	Optional<Batch> findBySourceOrderNoAndProductId(String sourceOrderNo, Long productId);
	List<Batch> findByProductionManagerEmailIgnoreCaseAndQualityStatusOrderByQualityInspectedAtDesc(String productionManagerEmail, String qualityStatus);
}

