package com.lanchat.infrastructure.websocket;

import com.lanchat.infrastructure.config.LanChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 心跳超时监控单元测试（FR-08）：35s 无入站消息 → system 提示 + code 1001 关闭。
 */
class HeartbeatMonitorTest {

    private static LanChatProperties properties() {
        return new LanChatProperties(
                new LanChatProperties.Jwt("unit-test-secret-key-0123456789-abcdefghij", 24),
                new LanChatProperties.Upload("data/uploads", 20L * 1024 * 1024),
                new LanChatProperties.History(50),
                new LanChatProperties.Heartbeat(35_000L),
                new LanChatProperties.RateLimit(5, 20));
    }

    @Test
    void inactiveSessionShouldReceiveSystemFrameThenCloseWith1001() {
        SessionRegistry registry = mock(SessionRegistry.class);
        WsFrameCodec codec = mock(WsFrameCodec.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");
        SessionRegistry.ClientSession client = new SessionRegistry.ClientSession(
                1L, "alice", session, new AtomicLong(0L));
        when(registry.isShuttingDown()).thenReturn(false);
        when(registry.findInactive(anyLong(), eq(35_000L))).thenReturn(List.of(client));
        when(codec.systemFrame(anyString())).thenReturn("{\"type\":\"system\"}");
        HeartbeatMonitor monitor = new HeartbeatMonitor(registry, codec, properties());

        monitor.closeTimedOutSessions();

        verify(registry).send(client, "{\"type\":\"system\"}");
        verify(registry).close(eq(client), argThat(status -> status.getCode() == 1001));
    }

    @Test
    void shouldDoNothingWhileServerIsShuttingDown() {
        SessionRegistry registry = mock(SessionRegistry.class);
        WsFrameCodec codec = mock(WsFrameCodec.class);
        when(registry.isShuttingDown()).thenReturn(true);
        HeartbeatMonitor monitor = new HeartbeatMonitor(registry, codec, properties());

        monitor.closeTimedOutSessions();

        verify(registry).isShuttingDown();
        verify(registry, never()).findInactive(anyLong(), anyLong());
        verify(registry, never()).send(org.mockito.ArgumentMatchers.any(), anyString());
        verify(registry, never()).close(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoMoreInteractions(registry);
    }

    @Test
    void registryShouldDetectInactiveSessionByLastActivity() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-2");
        long now = System.currentTimeMillis();
        registry.register(1L, "alice", session);
        // 伪造最后活动时间为 2 分钟前
        registry.findBySessionId("session-2").orElseThrow().lastActivity().set(now - 120_000L);

        List<SessionRegistry.ClientSession> inactive = registry.findInactive(now, 35_000L);

        assertThat(inactive).hasSize(1);
        assertThat(inactive.get(0).userId()).isEqualTo(1L);
        assertThat(registry.findInactive(now, 1_000_000L)).isEmpty();
        assertThat(CloseStatus.NORMAL.getCode()).isEqualTo(1000);
    }
}
