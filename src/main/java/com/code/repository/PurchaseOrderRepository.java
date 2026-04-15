package com.code.repository;

import com.code.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
	List<PurchaseOrder> findAllByOrderByOrderDateDesc();
	List<PurchaseOrder> findBySupplierIdOrderByOrderDateDesc(Long supplierId);
	boolean existsByPoNo(String poNo);
}

