package com.code.repository;

import com.code.entity.ProductionMaterialRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionMaterialRequestItemRepository extends JpaRepository<ProductionMaterialRequestItem, Long> {
}

