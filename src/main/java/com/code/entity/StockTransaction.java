package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transaction")
@Data
/*
 * 库存流水实体。
 *
 * <p>`InventoryItem` 负责记录“现在还有多少库存”，而 `StockTransaction` 负责回答“库存为什么变化、何时变化、谁操作的、关联哪张业务单据”。
 * 在企业系统里，库存余额与库存流水必须同时存在，前者支撑高频查询，后者支撑审计、追责、对账与问题排查。</p>
 */
public class StockTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流水号。 */
    private String transactionNo;

    /** 发生变动的产品/物料。 */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 发生变动的仓库。 */
    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** 变动数量。 */
    private Double changeQuantity;

    /**
     * 流水方向或类型。
     *
     * <p>常见值如 IN、OUT，用来表达入库或出库；有时也会结合 relatedType 一起还原更具体的业务语义。</p>
     */
    private String transactionType;

    /** 批次/lot 信息，用于追溯。 */
    private String lot;

    /**
     * 关联业务类型。
     *
     * <p>例如 SALES_ORDER、PURCHASE_ORDER、PRODUCTION_MATERIAL_REQUEST，帮助把库存动作映射回源业务。</p>
     */
    private String relatedType;

    /** 关联业务主键。 */
    private Long relatedId;

    /** 面向人工排查的备注说明。 */
    private String remark;

    /** 操作人 ID。 */
    private Long createdBy;

    /** 操作人姓名。 */
    private String createdByName;

    /** 流水创建时间。 */
    private LocalDateTime createdAt = LocalDateTime.now();
}

