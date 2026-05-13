package com.code.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
/*
 * 统一实时通知消息模型。
 *
 * <p>系统中的销售、采购、生产、仓库、质检等流程都会通过 WebSocket/STOMP 推送异步事件。这里故意不为每类通知定义完全不同的 DTO，
 * 而是抽象出一个通用信封：消息类型 + 业务实体 + 业务主键 + 载荷 + 时间戳。这样前端可以先按 messageType/entity 做统一分发，
 * 再决定是否展开 payload 渲染详情。</p>
 */
public class NotificationMessage {
    /**
     * 消息业务类型，例如订单状态变化、领料申请创建、采购待处理。
     */
    private String messageType;

    /**
     * 关联业务实体名称，例如 SalesOrder、ProductionMaterialRequest。
     */
    private String entity;

    /**
     * 关联业务主键，前端可据此做局部刷新或跳转详情。
     */
    private Long entityId;

    /**
     * 具体载荷。
     *
     * <p>使用 Object 保持消息信封通用性，代价是编译期类型约束较弱，前后端需要依赖 messageType 约定来正确解析。</p>
     */
    private Object payload;

    /**
     * 消息生成时间。
     */
    private LocalDateTime timestamp = LocalDateTime.now();
}

