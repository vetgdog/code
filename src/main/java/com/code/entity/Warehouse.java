package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouses")
@Data
/*
 * 仓库主数据实体。
 *
 * <p>仓库在本项目中不仅是一个静态地址概念，更是库存余额、库存流水、采购收货、生产领料、成品入库等业务动作的空间归属维度。
 * `InventoryItem` 与 `StockTransaction` 都会依赖它来表达“货在什么仓里发生了什么变化”。</p>
 */
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 仓库编码。
     *
     * <p>编码比名称更适合作为程序内稳定标识，因为名称可能因业务口径调整而变化，但编码通常需要保持不变以兼容脚本、默认值和历史数据。</p>
     */
    @Column(nullable = false, unique = true)
    private String code;

    /**
     * 仓库名称，面向业务展示，例如“原材料仓库”“成品仓库”。
     */
    @Column(nullable = false)
    private String name;

    /**
     * 仓库所在区域或库区描述。
     */
    private String location;

    /**
     * 仓库负责人用户 ID。
     *
     * <p>当前实现以 Long 保存而非直接映射 User 关联，优点是结构简单、对历史表变更侵入小；缺点是缺少 JPA 导航能力，
     * 若后续仓库负责人页面交互变复杂，可考虑改成 ManyToOne。</p>
     */
    private Long manager;

    /**
     * 主数据创建时间，便于初始化脚本和后台维护操作留痕。
     */
    private LocalDateTime createdAt = LocalDateTime.now();
}

