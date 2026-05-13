package com.code.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
/*
 * 实时通知发送服务。
 *
 * <p>该类对外只暴露一个很薄的 `broadcast` 方法，看起来简单，但它的价值在于把业务服务层与底层 STOMP 发送 API 解耦。
 * 订单、采购、生产、质检等服务只需要关心“把什么业务事件发到哪个 topic”，而不必直接依赖消息模板细节。</p>
 */
public class NotificationService {

    /**
     * Spring 提供的 STOMP 消息模板。
     *
     * <p>`convertAndSend` 会把对象转换为消息体并发送到 broker 管理的订阅地址，是当前项目实时通知的核心出口。</p>
     */
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void broadcast(String topic, NotificationMessage msg) {
        // topic 约定通常形如 /topic/production、/topic/orders/warehouse。
        // 统一由服务层拼接地址，前端按业务角色订阅相应主题，即可实现跨页面实时刷新。
        messagingTemplate.convertAndSend(topic, msg);
    }
}

