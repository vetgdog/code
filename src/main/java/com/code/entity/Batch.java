package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch")
@Data
/*
 * 生产批次实体。
 *
 * <p>批次是生产与质检之间最核心的追溯载体。生产任务完成后生成批次，质检员针对批次做检验，仓库和订单履约再根据批次是否合格决定能否进入成品库存。
 * 也就是说，`Batch` 连接了生产任务、产品、订单来源、质检结果和后续追溯查询。</p>
 */
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次号。 */
    @Column(nullable = false, unique = true)
    private String batchNo;

    /** 对应成品。 */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 该批次产出数量。 */
    private Double quantity;

    /** 生产完成/制造日期。 */
    private LocalDateTime manufactureDate;

    /** 失效日期；当前并非所有产品都会使用。 */
    private LocalDateTime expiryDate;

    /** 来源销售订单号，便于从批次反查订单链路。 */
    private String sourceOrderNo;

    /**
     * 质检状态。
     *
     * <p>例如待检、合格、不合格，是控制订单能否继续推进到成品入库/发货的重要门禁字段。</p>
     */
    private String qualityStatus = "待检";

    /** 质检备注。 */
    private String qualityRemark;

    /** 完成质检的时间。 */
    private LocalDateTime qualityInspectedAt;

    /** 质检员 ID。 */
    private Long qualityInspectorId;

    /** 质检员姓名。 */
    private String qualityInspectorName;

    /** 生产经理邮箱，用于质检结果通知。 */
    private String productionManagerEmail;

    /** 生产经理姓名。 */
    private String productionManagerName;

    /**
     * 来源生产任务。
     */
    @ManyToOne
    @JoinColumn(name = "production_task_id")
    private ProductionTask productionTask;

    /** 批次创建时间。 */
    private LocalDateTime createdAt = LocalDateTime.now();
}

