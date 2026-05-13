package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_material_request")
@Data
/*
 * 生产领料申请实体。
 *
 * <p>它是生产、仓库、采购三方围绕“原材料是否已准备好”进行协同的主单。
 * 当销售订单进入生产后，生产经理通过这张单声明需要哪些原材料；仓库审核后要么直接发料，要么因缺料触发采购补料；随后随着生产完工和成品入库完成整条闭环。</p>
 */
public class ProductionMaterialRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 领料申请号。 */
    @Column(nullable = false, unique = true)
    private String requestNo;

    /** 对应销售订单。 */
    @ManyToOne
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    /** 对应本次生产的成品。 */
    @ManyToOne
    @JoinColumn(name = "finished_product_id")
    private Product finishedProduct;

    /**
     * 原材料明细集合。
     */
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionMaterialRequestItem> items;

    /** 领料申请状态，例如待仓库备料、待采购补料、已备料待生产等。 */
    private String status = "待仓库备料";

    /** 生产侧申请备注。 */
    private String requestNote;

    /** 仓库审核备注。 */
    private String warehouseNote;

    /**
     * 是否已触发过采购补料。
     *
     * <p>这是流程中的幂等保护位，避免同一张缺料申请被仓库重复审核时生成多张采购申请。</p>
     */
    private Boolean procurementTriggered = false;

    /** 发起人 ID。 */
    private Long createdBy;

    /** 发起人姓名。 */
    private String createdByName;

    /** 发起人邮箱，可用于定向通知生产经理。 */
    private String createdByEmail;

    /** 仓库审核人 ID。 */
    private Long warehouseReviewedBy;

    /** 仓库审核人姓名。 */
    private String warehouseReviewedByName;

    /** 申请创建时间。 */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 仓库审核时间。 */
    private LocalDateTime warehouseReviewedAt;

    /** 仓库完成原材料出库时间。 */
    private LocalDateTime materialsIssuedAt;

    /** 生产完工时间。 */
    private LocalDateTime productionCompletedAt;

    /** 成品入库完成时间。 */
    private LocalDateTime warehousedAt;
}

