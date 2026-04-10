package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sales_order")
@Data
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNo;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private LocalDateTime orderDate = LocalDateTime.now();
    private String status = "NEW";
    private Double totalAmount = 0.0;
    private LocalDateTime deliveryDate;
    private String shippingAddress;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

    private Long createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
}

