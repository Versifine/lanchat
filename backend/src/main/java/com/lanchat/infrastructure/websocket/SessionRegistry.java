package com.lanchat.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 在线会话表（线程安全，基于 ConcurrentHashMap）。
 *
 * <p>维护两张索引：</p>
 * <ul>
 *   <li>{@code onlineUsers: userId -> ClientSession}：单播、广播、在线状态查询；</li>
 *   <li>{@code sessionOwners: sessionId -> userId}：连接关闭时反查用户，并保证
 *       “重复登录踢掉旧连接”时旧连接关闭不会误删新连接。</li>
 * </ul>
 *
 * <p>发送统一走 {@link org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator}
 * 包装后的会话，避免广播线程与业务线程并发 sendMessage 触发 IllegalStateException。</p>
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<Long, ClientSession> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionOwners = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown = false;

    /**
     * 注册在线会话。
     *
     * @return 同一用户之前的旧会话（用于重复登录踢下线）；没有旧会话时返回 null
     */
    public ClientSession register(long userId, String username, WebSocketSession session) {
        ClientSession clientSession = new ClientSession(userId, username, session,
                new AtomicLong(System.currentTimeMillis()));
        ClientSession previous = onlineUsers.put(userId, clientSession);
        sessionOwners.put(session.getId(), userId);
        if (previous != null) {
            sessionOwners.remove(previous.session().getId());
        }
        return previous;
    }

    /**
     * 注销会话。
     *
     * @return 仅当该会话确实是用户“当前在线连接”时返回 userId；被踢掉的旧连接返回 empty，
     * 从而不会误广播离线状态
     */
    public OptionalLong unregister(String sessionId) {
        Long userId = sessionOwners.remove(sessionId);
        if (userId == null) {
            return OptionalLong.empty();
        }
        ClientSession current = onlineUsers.get(userId);
        if (current != null && current.session().getId().equals(sessionId)) {
            onlineUsers.remove(userId, current);
            return OptionalLong.of(userId);
        }
        return OptionalLong.empty();
    }

    public Optional<ClientSession> findByUserId(long userId) {
        return Optional.ofNullable(onlineUsers.get(userId));
    }

    public Optional<ClientSession> findBySessionId(String sessionId) {
        Long userId = sessionOwners.get(sessionId);
        return userId == null ? Optional.empty() : findByUserId(userId);
    }

    /** 当前在线会话快照（用于广播遍历，避免直接遍历并发 Map）。 */
    public List<ClientSession> snapshot() {
        return List.copyOf(onlineUsers.values());
    }

    /** 当前在线用户 id 集合。 */
    public Set<Long> onlineUserIds() {
        return Set.copyOf(onlineUsers.keySet());
    }

    public boolean isOnline(long userId) {
        return onlineUsers.containsKey(userId);
    }

    /** 记录一次入站消息，用于心跳超时判定（35s 无任何消息即离线）。 */
    public void touch(String sessionId) {
        Optional<ClientSession> clientSession = findBySessionId(sessionId);
        clientSession.ifPresent(client -> client.lastActivity().set(System.currentTimeMillis()));
    }

    /**
     * 找出超过 timeoutMs 没有任何入站消息的会话。
     *
     * <p>只返回、不移除：由关闭连接触发的 afterConnectionClosed 回调统一注销并广播下线，
     * 同时保留“关闭失败下次继续扫描”的重试语义。</p>
     */
    public List<ClientSession> findInactive(long now, long timeoutMs) {
        return onlineUsers.values().stream()
                .filter(client -> now - client.lastActivity().get() > timeoutMs)
                .toList();
    }

    /**
     * 给单个会话发送文本帧。
     *
     * @return 发送成功返回 true；会话关闭/发送异常返回 false（异常时顺带关闭连接）
     */
    public boolean send(ClientSession target, String payload) {
        WebSocketSession session = target.session();
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("WebSocket 发送失败: userId={}, reason={}", target.userId(), e.getMessage());
            close(target, CloseStatus.SERVER_ERROR);
            return false;
        }
    }

    /** 广播文本帧给所有在线会话。 */
    public void broadcast(String payload) {
        for (ClientSession target : snapshot()) {
            send(target, payload);
        }
    }

    /** 关闭单个会话（由容器回调 afterConnectionClosed 完成注销）。 */
    public void close(ClientSession target, CloseStatus status) {
        WebSocketSession session = target.session();
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("WebSocket 关闭异常: userId={}, reason={}", target.userId(), e.getMessage());
        }
    }

    /** 关闭全部会话（服务端优雅关闭）。 */
    public void closeAll(CloseStatus status) {
        for (ClientSession target : snapshot()) {
            close(target, status);
        }
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /** 服务端关闭标记：关闭阶段不再广播在线列表变化。 */
    public void markShuttingDown() {
        this.shuttingDown = true;
    }

    /**
     * 在线会话（WebSocketSession 已用并发发送装饰器包装）。
     */
    public record ClientSession(long userId,
                                String username,
                                WebSocketSession session,
                                AtomicLong lastActivity) {
    }
}
