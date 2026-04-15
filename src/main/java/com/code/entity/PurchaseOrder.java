package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchase_order")
@Data
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String poNo;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private LocalDateTime orderDate = LocalDateTime.now();
    private String status = "CREATED";
    private Double totalAmount = 0.0;
    private Long createdBy;
    private String supplierNote;
    private String procurementNote;
    private String warehouseNote;
    private LocalDateTime shippedAt;
    private LocalDateTime notifiedWarehouseAt;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items;
}

