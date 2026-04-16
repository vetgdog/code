package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_plan")
@Data
public class ProductionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String planNo;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Double plannedQuantity;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status = "PLANNED";
    private Long createdBy;
    private String completedByEmail;
    private String completedByName;
    private LocalDateTime createdAt = LocalDateTime.now();
}

