package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "production_material_request_item")
@Data
public class ProductionMaterialRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "request_id")
    @JsonIgnore
    private ProductionMaterialRequest request;

    @ManyToOne
    @JoinColumn(name = "material_product_id")
    private Product materialProduct;

    private Double requiredQuantity = 0.0;
    private Double issuedQuantity = 0.0;
    private Double availableQuantitySnapshot = 0.0;
    private Double shortageQuantitySnapshot = 0.0;
}

