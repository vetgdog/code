package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_task")
@Data
public class ProductionTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String taskNo;

    @ManyToOne
    @JoinColumn(name = "production_plan_id")
    private ProductionPlan productionPlan;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Double scheduledQuantity;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status = "PENDING";
    private Long assignedTo;
    private LocalDateTime createdAt = LocalDateTime.now();
}

