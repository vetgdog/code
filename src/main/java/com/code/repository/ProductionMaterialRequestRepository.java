package com.code.repository;

import com.code.entity.ProductionMaterialRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionMaterialRequestRepository extends JpaRepository<ProductionMaterialRequest, Long> {
    List<ProductionMaterialRequest> findAllByOrderByCreatedAtDesc();
    List<ProductionMaterialRequest> findBySalesOrderIdOrderByCreatedAtDesc(Long salesOrderId);
}

