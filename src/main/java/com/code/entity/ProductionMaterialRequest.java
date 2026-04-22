package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_material_request")
@Data
public class ProductionMaterialRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestNo;

    @ManyToOne
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @ManyToOne
    @JoinColumn(name = "finished_product_id")
    private Product finishedProduct;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionMaterialRequestItem> items;

    private String status = "待仓库备料";
    private String requestNote;
    private String warehouseNote;
    private Boolean procurementTriggered = false;

    private Long createdBy;
    private String createdByName;
    private String createdByEmail;

    private Long warehouseReviewedBy;
    private String warehouseReviewedByName;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime warehouseReviewedAt;
    private LocalDateTime materialsIssuedAt;
    private LocalDateTime productionCompletedAt;
    private LocalDateTime warehousedAt;
}

