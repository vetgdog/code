package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "production_material_request_item")
@Data
/*
 * 生产领料申请明细实体。
 *
 * <p>它记录一张领料申请里每种原材料各需要多少、实际已发多少，以及仓库审核当时看到的可用量和缺口快照。
 * 这些快照字段很重要，因为它们能把“当时为什么判定缺料/可发料”保留下来，方便后续审计与复盘。</p>
 */
public class ProductionMaterialRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属领料申请。 */
    @ManyToOne
    @JoinColumn(name = "request_id")
    @JsonIgnore
    private ProductionMaterialRequest request;

    /** 对应原材料产品。 */
    @ManyToOne
    @JoinColumn(name = "material_product_id")
    private Product materialProduct;

    /** 申请需要的原材料数量。 */
    private Double requiredQuantity = 0.0;

    /** 仓库实际已发料数量。 */
    private Double issuedQuantity = 0.0;

    /** 仓库审核时记录的可用库存快照。 */
    private Double availableQuantitySnapshot = 0.0;

    /** 仓库审核时记录的缺口数量快照。 */
    private Double shortageQuantitySnapshot = 0.0;
}

