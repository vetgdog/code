package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transaction")
@Data
public class StockTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionNo;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private Double changeQuantity;
    private String transactionType;
    private String lot;
    private String relatedType;
    private Long relatedId;
    private String remark;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt = LocalDateTime.now();
}

