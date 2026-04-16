package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quality_record")
@Data
public class QualityRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Long inspector;
    private String inspectorName;
    private LocalDateTime inspectionDate = LocalDateTime.now();
    private String result;
    private String remarks;
    private String notifiedProductionManagerEmail;
    private Boolean notificationSent = false;
    private LocalDateTime notifiedAt;
}

