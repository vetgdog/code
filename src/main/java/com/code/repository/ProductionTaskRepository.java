package com.code.repository;

import com.code.entity.ProductionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionTaskRepository extends JpaRepository<ProductionTask, Long> {
    List<ProductionTask> findByAssignedTo(Long userId);
    List<ProductionTask> findByProductionPlanId(Long planId);
}

