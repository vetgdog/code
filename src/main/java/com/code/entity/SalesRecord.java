package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_record")
@Data
public class SalesRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String recordNo;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(nullable = false)
    private String orderNo;

    private Double totalAmount;
    private String customerName;
    private String shippingAddress;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
}

