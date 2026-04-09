package com.code.entity;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "bom_item")
@Data
public class BomItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "bom_id")
    private Bom bom;

    @ManyToOne
    @JoinColumn(name = "component_product_id")
    private Product componentProduct;

    private Double quantity;
    private String unit;
    private String notes;
}

