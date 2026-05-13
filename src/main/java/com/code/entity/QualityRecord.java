package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quality_record")
@Data
/*
 * 质检记录实体。
 *
 * <p>如果说 `Batch` 是“被检对象”，那么 `QualityRecord` 就是“这次检验行为的日志”。
 * 它独立保存检验时间、检验人、结果、备注与通知发送状态，便于后续做质检台账、审计追溯和异常复盘。</p>
 */
public class QualityRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对应被检批次。 */
    @ManyToOne
    @JoinColumn(name = "batch_id")
    private Batch batch;

    /** 对应产品。冗余保存有利于列表查询与统计。 */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 检验人员 ID。 */
    private Long inspector;

    /** 检验人员姓名。 */
    private String inspectorName;

    /** 检验时间。 */
    private LocalDateTime inspectionDate = LocalDateTime.now();

    /** 检验结论，如合格/不合格。 */
    private String result;

    /** 质检备注。 */
    private String remarks;

    /** 已通知的生产经理邮箱。 */
    private String notifiedProductionManagerEmail;

    /**
     * 是否已发送通知。
     *
     * <p>这是一个典型的“消息副作用落库标志”，用于避免异常重试或重复操作时重复推送同一条质检通知。</p>
     */
    private Boolean notificationSent = false;

    /** 通知发送时间。 */
    private LocalDateTime notifiedAt;
}

