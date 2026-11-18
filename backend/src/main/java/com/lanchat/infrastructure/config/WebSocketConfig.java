package com.lanchat.infrastructure.config;

import com.lanchat.adapter.in.ws.ChatWebSocketHandler;
import com.lanchat.adapter.in.ws.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置：
 * <ul>
 *   <li>注册 {@code /ws/chat} 端点与 JWT 握手拦截器（握手阶段校验 token，失败 HTTP 401 拒绝连接）；</li>
 *   <li>契约规定单帧最大 16KB，这里设置容器缓冲区大小；</li>
 *   <li>允许任意来源（局域网浏览器直连场景），认证由握手拦截器保证。</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 契约：双方消息均为 JSON 文本帧，最大 16KB。 */
    private static final int MAX_MESSAGE_BUFFER_BYTES = 16 * 1024;

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 约束 WebSocket 容器缓冲区，避免超大帧占用内存（超长文本由业务层返回 error 1008）。
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER_BYTES);
        return container;
    }
}
