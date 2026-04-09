package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch")
@Data
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchNo;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Double quantity;
    private LocalDateTime manufactureDate;
    private LocalDateTime expiryDate;

    @ManyToOne
    @JoinColumn(name = "production_task_id")
    private ProductionTask productionTask;

    private LocalDateTime createdAt = LocalDateTime.now();
}

