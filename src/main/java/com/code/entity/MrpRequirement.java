package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mrp_requirement")
@Data
public class MrpRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Double requiredQuantity;
    private LocalDateTime requiredDate;
    private String sourceType;
    private Long relatedId;
    private String status = "OPEN";
    private LocalDateTime createdAt = LocalDateTime.now();
}

