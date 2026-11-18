package com.lanchat.infrastructure.lifecycle;

import com.lanchat.infrastructure.websocket.SessionRegistry;
import com.lanchat.infrastructure.websocket.WsFrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import jakarta.annotation.PreDestroy;

/**
 * 应用生命周期：启动日志 + 优雅关闭（FR-09）。
 *
 * <p>优雅关闭流程：先给所有在线连接推送 system 提示，再以 code 1001 关闭全部 WS 会话，
 * 最后交由 Spring 关闭连接池；关闭阶段通过 {@link SessionRegistry#markShuttingDown()}
 * 阻止“下线广播”继续发送，避免关闭期异常。</p>
 */
@Component
public class LanChatLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LanChatLifecycle.class);

    private final SessionRegistry sessionRegistry;
    private final WsFrameCodec frameCodec;
    private final int serverPort;

    public LanChatLifecycle(SessionRegistry sessionRegistry,
                            WsFrameCodec frameCodec,
                            @Value("${server.port:8081}") int serverPort) {
        this.sessionRegistry = sessionRegistry;
        this.frameCodec = frameCodec;
        this.serverPort = serverPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("LanChat 后端启动完成: REST=http://localhost:{}/api, WebSocket=ws://localhost:{}/ws/chat, Swagger=http://localhost:{}/swagger-ui.html",
                serverPort, serverPort, serverPort);
    }

    @PreDestroy
    public void shutdown() {
        sessionRegistry.markShuttingDown();
        int onlineCount = sessionRegistry.snapshot().size();
        log.info("LanChat 后端开始优雅关闭：通知并断开 {} 个 WebSocket 连接", onlineCount);
        String systemFrame = frameCodec.systemFrame("服务端即将关闭，连接已断开");
        for (SessionRegistry.ClientSession client : sessionRegistry.snapshot()) {
            sessionRegistry.send(client, systemFrame);
            sessionRegistry.close(client, new CloseStatus(1001, "server shutdown"));
        }
        log.info("WebSocket 连接已全部关闭，等待 Spring 关闭连接池");
    }
}
