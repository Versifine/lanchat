package com.lanchat.infrastructure.websocket;

import com.lanchat.infrastructure.config.LanChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.List;

/**
 * 心跳超时监控（FR-08）。
 *
 * <p>契约：客户端每 10s ping 一次，服务端 35s 内没有收到任何消息则判定离线并关闭连接（code 1001）。
 * 这里每 5s 扫描一次在线会话表，关闭超时会话；关闭动作会触发容器回调
 * {@code afterConnectionClosed}，由 WebSocket Handler 统一注销并广播在线列表。</p>
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);
    private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

    private final SessionRegistry sessionRegistry;
    private final WsFrameCodec frameCodec;
    private final LanChatProperties properties;

    public HeartbeatMonitor(SessionRegistry sessionRegistry,
                            WsFrameCodec frameCodec,
                            LanChatProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.frameCodec = frameCodec;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, initialDelay = SWEEP_INTERVAL_MILLIS)
    public void closeTimedOutSessions() {
        if (sessionRegistry.isShuttingDown()) {
            return;
        }
        long now = System.currentTimeMillis();
        long timeoutMillis = properties.heartbeat().timeoutMs();
        List<SessionRegistry.ClientSession> inactive = sessionRegistry.findInactive(now, timeoutMillis);
        for (SessionRegistry.ClientSession client : inactive) {
            log.info("WebSocket 心跳超时，关闭连接: userId={}, sessionId={}",
                    client.userId(), client.session().getId());
            sessionRegistry.send(client, frameCodec.systemFrame("连接超时，已断开，请重新连接"));
            sessionRegistry.close(client, new CloseStatus(1001, "heartbeat timeout"));
        }
    }
}
