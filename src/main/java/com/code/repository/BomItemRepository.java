package com.code.repository;

import com.code.entity.BomItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BomItemRepository extends JpaRepository<BomItem, Long> {
}

