package com.lanchat.infrastructure.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线会话表单元测试：注册/注销、重复登录（EV-FUNC-15）的旧连接清理语义、发送失败处理。
 */
class SessionRegistryTest {

    @Test
    void registerShouldTrackSingleSessionPerUser() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession first = session("s-1");

        assertThat(registry.register(1L, "alice", first)).isNull();
        assertThat(registry.isOnline(1L)).isTrue();
        assertThat(registry.onlineUserIds()).containsExactly(1L);
        assertThat(registry.findByUserId(1L)).isPresent();
        assertThat(registry.findBySessionId("s-1")).isPresent();
        assertThat(registry.snapshot()).hasSize(1);
    }

    @Test
    void duplicateLoginShouldKickOldSessionWithoutLosingNewOne() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession oldSession = session("old");
        WebSocketSession newSession = session("new");
        registry.register(1L, "alice", oldSession);

        SessionRegistry.ClientSession previous = registry.register(1L, "alice", newSession);

        assertThat(previous).isNotNull();
        assertThat(previous.session().getId()).isEqualTo("old");
        // 旧连接关闭后注销：不得误删新连接
        assertThat(registry.unregister("old")).isEmpty();
        assertThat(registry.isOnline(1L)).isTrue();
        assertThat(registry.findByUserId(1L).orElseThrow().session().getId()).isEqualTo("new");
        // 新连接关闭后正常下线
        assertThat(registry.unregister("new")).isEqualTo(OptionalLong.of(1L));
        assertThat(registry.isOnline(1L)).isFalse();
    }

    @Test
    void unregisterUnknownSessionShouldReturnEmpty() {
        SessionRegistry registry = new SessionRegistry();

        assertThat(registry.unregister("never-seen")).isEmpty();
        assertThat(registry.findBySessionId("never-seen")).isEmpty();
    }

    @Test
    void sendShouldReturnFalseAndCloseWhenSessionIsClosedOrThrows() throws IOException {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.getId()).thenReturn("closed");
        when(closed.isOpen()).thenReturn(false);
        SessionRegistry.ClientSession closedClient = new SessionRegistry.ClientSession(
                1L, "alice", closed, new java.util.concurrent.atomic.AtomicLong());
        assertThat(registry.send(closedClient, "{}")).isFalse();
        verify(closed, never()).sendMessage(any());

        WebSocketSession failing = mock(WebSocketSession.class);
        when(failing.getId()).thenReturn("failing");
        when(failing.isOpen()).thenReturn(true);
        try {
            doThrow(new IOException("broken pipe")).when(failing).sendMessage(any(TextMessage.class));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SessionRegistry.ClientSession failingClient = new SessionRegistry.ClientSession(
                2L, "bob", failing, new java.util.concurrent.atomic.AtomicLong());
        assertThat(registry.send(failingClient, "{}")).isFalse();
        verify(failing).close(any(CloseStatus.class));
    }

    @Test
    void broadcastShouldSendToAllOnlineSessions() throws IOException {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession first = session("s-1");
        WebSocketSession second = session("s-2");
        registry.register(1L, "alice", first);
        registry.register(2L, "bob", second);

        registry.broadcast("{\"type\":\"userList\"}");

        verify(first).sendMessage(any(TextMessage.class));
        verify(second).sendMessage(any(TextMessage.class));
        assertThat(registry.snapshot()).hasSize(2);
    }

    @Test
    void shuttingDownFlagAndFindByUserIdShouldWork() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = session("s-1");
        registry.register(1L, "alice", session);

        assertThat(registry.findByUserId(99L)).isEqualTo(Optional.empty());
        assertThat(registry.isShuttingDown()).isFalse();
        registry.markShuttingDown();
        assertThat(registry.isShuttingDown()).isTrue();
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
