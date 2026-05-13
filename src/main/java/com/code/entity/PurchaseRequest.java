package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_request")
@Data
/*
 * 采购申请实体。
 *
 * <p>它代表“需要补某种原材料/物料”的业务意图，通常来源于库存预警、生产领料缺口或人工发起采购。
 * 采购申请本身还不是最终采购单，而是采购流程的上游需求单，后续可被采购经理合并、转换成正式 `PurchaseOrder`。</p>
 */
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 采购申请号。 */
    @Column(nullable = false, unique = true)
    private String requestNo;

    /** 发起人用户 ID。 */
    private Long requestedBy;

    /** 发起人显示名，便于列表直接展示，不必每次回表查用户。 */
    private String requestedByName;

    /**
     * 申请采购的物料。
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 申请数量。 */
    private Double requestedQuantity = 0.0;

    /**
     * 目标供应商。
     *
     * <p>当前直接关联到统一用户表中的供应商账号，体现系统已把采购协同收口到统一账号模型中。</p>
     */
    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private User supplier;

    /** 申请时间。 */
    private LocalDateTime requestDate = LocalDateTime.now();

    /**
     * 申请状态。
     *
     * <p>例如 OPEN、CONVERTED 等，用于表达是否已被转换成采购单。</p>
     */
    private String status = "OPEN";

    /** 补料原因、来源单据、说明等备注信息。 */
    private String notes;
}

