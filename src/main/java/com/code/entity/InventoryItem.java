package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {@UniqueConstraint(columnNames = {"product_id","warehouse_id"})})
@Data
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private Double quantity = 0.0;
    private Double reservedQuantity = 0.0;
    private String lot;

    private LocalDateTime updatedAt = LocalDateTime.now();
}

