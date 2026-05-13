package com.code.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "purchase_order_item")
@Data
/*
 * 采购订单明细实体。
 *
 * <p>它记录采购单里每一行具体采购的物料、数量、单价和行金额，是采购订单的子实体。
 * 仓库收货、成本统计、供应商履约明细通常都会追踪到这一层。</p>
 */
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属采购单。
     *
     * <p>`@JsonIgnore` 用于避免双向关联在序列化时产生无限递归。</p>
     */
    @ManyToOne
    @JoinColumn(name = "purchase_order_id")
    @JsonIgnore
    private PurchaseOrder purchaseOrder;

    /** 被采购的物料。 */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 采购数量。 */
    private Double quantity;

    /** 下单单价。 */
    private Double unitPrice;

    /** 行金额，通常等于 quantity * unitPrice。 */
    private Double lineTotal;
}

