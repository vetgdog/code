package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_request")
@Data
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestNo;

    private Long requestedBy;
    private String requestedByName;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Double requestedQuantity = 0.0;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private User supplier;

    private LocalDateTime requestDate = LocalDateTime.now();
    private String status = "OPEN";
    private String notes;
}

