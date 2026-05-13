package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchase_order")
@Data
/*
 * 采购订单实体。
 *
 * <p>它是采购经理与供应商、仓库三方协同的正式执行单据：采购申请只是“想买”，采购订单才是“确定向哪家供应商买什么、多少钱、进入什么履约状态”。
 * 采购下单、供应商确认、发货、通知仓库、收货入库等关键时间点都会落在这里。</p>
 */
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 采购单号。 */
    @Column(nullable = false, unique = true)
    private String poNo;

    /** 供应商账号。 */
    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private User supplier;

    /** 下单时间。 */
    private LocalDateTime orderDate = LocalDateTime.now();

    /**
     * 采购单状态。
     *
     * <p>通常会经历 CREATED、SHIPPED、WAREHOUSE_NOTIFIED、RECEIVED 等阶段，是采购履约状态机的主字段。</p>
     */
    private String status = "CREATED";

    /** 采购总金额。 */
    private Double totalAmount = 0.0;

    /** 创建采购单的内部操作者 ID。 */
    private Long createdBy;

    /** 创建采购单的内部操作者显示名。 */
    private String createdByName;

    /** 供应商备注。 */
    private String supplierNote;

    /** 采购侧备注。 */
    private String procurementNote;

    /** 仓库收货备注。 */
    private String warehouseNote;

    /** 供应商标记发货时间。 */
    private LocalDateTime shippedAt;

    /** 采购侧通知仓库待收货的时间。 */
    private LocalDateTime notifiedWarehouseAt;

    /** 仓库完成收货入库的时间。 */
    private LocalDateTime receivedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt = LocalDateTime.now();

    /*
     * 来源采购申请 ID 列表。
     *
     * <p>该字段只用于接口层传输和页面回显，不落库。它帮助前端/服务层表达“本采购单由哪些采购申请合并转换而来”。</p>
     */
    @Transient
    private List<Long> sourceRequestIds;

    /**
     * 采购单明细集合。
     */
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items;
}

