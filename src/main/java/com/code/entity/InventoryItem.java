package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {@UniqueConstraint(columnNames = {"product_id","warehouse_id"})})
@Data
/*
 * 库存余额实体。
 *
 * <p>它回答的是“某产品在某仓库当前还剩多少”，属于库存当前态快照；与之配套的 `StockTransaction` 则记录每次变化过程。
 * 企业系统通常需要“余额表 + 流水表”双表并存：余额用于高频查询，流水用于审计追溯。</p>
 */
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属产品/物料。
     *
     * <p>ManyToOne 说明多个库存记录可以指向同一产品。当前唯一约束使得默认模型是一种“每个产品在每个仓一条余额记录”的聚合设计。</p>
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * 所属仓库。
     */
    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /**
     * 当前实际库存余额。
     */
    private Double quantity = 0.0;

    /**
     * 已被其他流程预留但尚未真正出库/消耗的数量。
     *
     * <p>例如订单锁库时会增加 reservedQuantity。可用库存应按 `quantity - reservedQuantity` 计算，避免重复承诺同一批库存。</p>
     */
    private Double reservedQuantity = 0.0;

    /**
     * 批次/批号信息。
     *
     * <p>当前余额模型没有把 lot 纳入唯一约束，因此它更像一个附加追溯字段。若未来严格按批次管理库存，可考虑将 lot 升级为库存主维度之一。</p>
     */
    private String lot;

    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt = LocalDateTime.now();
}

