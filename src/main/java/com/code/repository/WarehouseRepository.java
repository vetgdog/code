package com.code.repository;

import com.code.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
	Optional<Warehouse> findByCodeIgnoreCase(String code);
}

