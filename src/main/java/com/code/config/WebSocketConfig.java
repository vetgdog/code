package com.code.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
/*
 * WebSocket/STOMP 配置。
 *
 * <p>项目中的“实时通知”并不是直接使用原始 WebSocket 文本帧，而是采用 Spring Message Broker + STOMP 协议的更高层抽象。
 * 这样做的好处是 topic 路由、订阅模型、消息模板发送都更统一，适合订单状态、库存预警、采购待办这类典型企业系统广播场景。</p>
 */
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // enableSimpleBroker 表示启用 Spring 内置的轻量 broker，
        // 适合当前单体应用内的中低规模实时通知需求；若未来消息量更大，可迁移到 RabbitMQ 等外部 broker。
        config.enableSimpleBroker("/topic", "/queue");

        // /app 前缀保留给客户端发往服务端的应用消息入口。
        // 当前项目主要使用服务端主动广播，但保留该前缀能为后续双向消息交互预留标准入口。
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 这里注册的是浏览器真正建立连接的 HTTP 握手端点。
        // 之所以使用 allowedOriginPatterns 而不是简单的 *，是为了兼容 Spring 5.3+ 对携带凭证跨域的限制。
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "https://localhost:*", "https://127.0.0.1:*")

                // SockJS 允许在浏览器或网络环境不支持原生 WebSocket 时自动降级，
                // 对开发环境和兼容性更友好，尤其适合前后端分离项目的本地联调。
                .withSockJS();
    }
}

