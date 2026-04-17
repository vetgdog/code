package com.code.repository;

import com.code.entity.ProductionWeeklyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionWeeklyPlanRepository extends JpaRepository<ProductionWeeklyPlan, Long> {
    Optional<ProductionWeeklyPlan> findByWeekStart(LocalDate weekStart);
    List<ProductionWeeklyPlan> findAllByOrderByWeekStartDesc();
}

