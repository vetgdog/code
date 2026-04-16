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
    private String sourceOrderNo;
    private String qualityStatus = "待检";
    private String qualityRemark;
    private LocalDateTime qualityInspectedAt;
    private Long qualityInspectorId;
    private String qualityInspectorName;
    private String productionManagerEmail;
    private String productionManagerName;

    @ManyToOne
    @JoinColumn(name = "production_task_id")
    private ProductionTask productionTask;

    private LocalDateTime createdAt = LocalDateTime.now();
}

