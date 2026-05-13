package com.code.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "order_item")
@Data
/*
 * 销售订单明细实体。
 *
 * <p>它承载“订单里具体买了什么、买多少、单价多少”这部分行项目数据，是 `SalesOrder` 聚合根的子实体。
 * 订单总额、缺货判断、生产计划拆分、销售统计通常都需要从这里读取产品与数量信息。</p>
 */
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属销售订单。
     *
     * <p>使用 `@JsonIgnore` 是为了避免接口序列化时出现 salesOrder -> items -> salesOrder 的循环引用。</p>
     */
    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private SalesOrder salesOrder;

    /**
     * 被订购的产品。
     *
     * <p>通常应是成品产品，但实体层本身不强制限制，真正的业务约束由服务层校验。</p>
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /** 购买数量。 */
    private Double quantity;

    /** 下单时锁定的成交单价。 */
    private Double unitPrice;

    /** 行金额，通常等于 quantity * unitPrice。 */
    private Double lineTotal;
}

