package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sales_order")
@Data
/*
 * 销售订单实体。
 *
 * <p>它是客户下单、销售审核、仓库核查、生产补货、质检放行、发货履约整条链路的业务主单。
 * 在领域建模上，`SalesOrder` 是一个订单聚合根：客户、订单时间、状态、总金额、交付时间、收货地址等头信息在这里维护，
 * 具体订购了哪些产品则由 `OrderItem` 作为子表承载。</p>
 */
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 订单号。
     *
     * <p>这是前后端交互、通知、库存关联、质检追溯里最常见的业务识别码，唯一约束保证订单在全系统范围可准确定位。</p>
     */
    @Column(nullable = false, unique = true)
    private String orderNo;

    /**
     * 下单客户。
     *
     * <p>ManyToOne 表示多个订单可以归属于同一个客户，是典型的客户-订单主从关系。</p>
     */
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** 下单时间。 */
    private LocalDateTime orderDate = LocalDateTime.now();

    /**
     * 订单状态。
     *
     * <p>该字段会随着审核、仓库核查、生产、质检、入库、发货等流程不断推进，是订单状态机的核心落点。</p>
     */
    private String status = "NEW";

    /**
     * 订单总金额。
     *
     * <p>通常由各行明细 `lineTotal` 汇总得出，便于列表查询和报表统计时直接使用，不必每次临时聚合子表。</p>
     */
    private Double totalAmount = 0.0;

    /** 承诺交付时间。 */
    private LocalDateTime deliveryDate;

    /** 收货地址。 */
    private String shippingAddress;

    /**
     * 订单明细集合。
     *
     * <p>`cascade = ALL` 说明保存/删除订单时会联动处理明细；`orphanRemoval = true` 表示当某条明细从集合中移除后会自动删除数据库记录，
     * 适合订单聚合根统一维护明细生命周期的场景。</p>
     */
    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

    /**
     * 创建人用户 ID。
     *
     * <p>当前仅保存 Long 而非直接关联 User，主要用于审计与按销售员筛选订单。</p>
     */
    private Long createdBy;

    /** 订单创建时间。 */
    private LocalDateTime createdAt = LocalDateTime.now();
}

