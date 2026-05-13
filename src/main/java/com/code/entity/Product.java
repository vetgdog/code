package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
/*
 * 产品主数据实体。
 *
 * <p>本项目采用“成品 + 原材料共用一张产品表”的统一建模方式：销售、生产、库存、采购都围绕 Product 展开，
 * 再通过 {@code productType} 区分是 FINISHED_GOOD 还是 RAW_MATERIAL。这样做的好处是库存模型、流水模型和周计划算法可以复用同一套产品主键，
 * 代价是某些字段只对某一类产品有意义，需要在业务层额外约束。</p>
 */
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 产品/物料编码。
     *
     * <p>SKU 是跨页面、跨模块最稳定的业务识别码，常用于采购、库存、销售、批次、导出报表之间的对齐。</p>
     */
    @Column(nullable = false, unique = true)
    private String sku;

    /**
     * 产品或原材料名称。
     */
    @Column(nullable = false)
    private String name;

    /**
     * 产品类型，默认成品。
     *
     * <p>该字段是统一产品表设计中的关键分流字段：销售订单、生产计划更关注成品，采购申请、领料申请更关注原材料。</p>
     */
    @Column(nullable = false)
    private String productType = "FINISHED_GOOD";

    /** 原材料分类，如钢材、化工辅料。对成品通常为空。 */
    private String materialCategory;

    /** 规格型号，兼顾产品型号与物料规格展示。 */
    private String specification;

    /**
     * 首选供应商。
     *
     * <p>当前以文本方式保存，便于快速展示和兼容历史数据；但文本关联缺少强约束，未来若采购协同更复杂，可升级为 supplier user/entity 的外键引用。</p>
     */
    private String preferredSupplier;

    /** 产地或来源地。 */
    private String origin;

    /** 面向页面说明、搜索与理解业务用途的补充描述。 */
    private String description;

    /** 库存与订单使用的计量单位，如 kg、台、箱。 */
    private String unit;

    /** 单件/单位重量，主要用于物流、成本或容量估算。 */
    private Double weight;

    /**
     * 标准单价。
     *
     * <p>销售下单与采购视图通常会用它做默认值，但关键交易仍应由服务端重新计算或校验，避免直接信任前端传价。</p>
     */
    private Double unitPrice = 0.0;

    /**
     * 安全库存。
     *
     * <p>库存预警、周计划补货建议都会参考它，体现“低于此值就需要关注”的业务阈值。</p>
     */
    private Double safetyStock = 0.0;

    /**
     * 采购提前期天数。
     *
     * <p>主要对原材料有意义，可供采购周计划、缺料补料建议和交期评估使用。</p>
     */
    private Integer leadTimeDays = 0;

    /**
     * 主数据创建时间。
     */
    private LocalDateTime createdAt = LocalDateTime.now();
}

