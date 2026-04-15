package com.code.repository;

import com.code.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
	Optional<Supplier> findByEmailIgnoreCase(String email);
}

