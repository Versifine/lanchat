package com.lanchat.adapter.in.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanchat.application.service.ChatService;
import com.lanchat.application.service.UserService;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.infrastructure.websocket.SessionRegistry;
import com.lanchat.infrastructure.websocket.WsFrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 聊天 WebSocket 处理器（协议适配层，只做“JSON 帧 ↔ 应用服务”的转换）。
 *
 * <p>连接生命周期（docs/api.md「连接生命周期」）：</p>
 * <ol>
 *   <li>握手：{@link JwtHandshakeInterceptor} 校验 token，失败 HTTP 401；</li>
 *   <li>建立：注册在线会话 → 重复登录则提示并踢掉旧连接 → 广播全量 userList → 补发离线私聊（offline=true）；</li>
 *   <li>消息：ping → pong；chat → ChatService 校验/落库/推送；业务错误回 error 帧（连接保持）；</li>
 *   <li>关闭：注销在线会话并广播新的 userList。</li>
 * </ol>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    /** 并发发送装饰器参数：单次发送超时与缓冲区上限。 */
    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 256 * 1024;

    private static final String TYPE_PING = "ping";
    private static final String TYPE_CHAT = "chat";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TO = "to";
    private static final String FIELD_CONTENT = "content";

    private final SessionRegistry sessionRegistry;
    private final WsFrameCodec frameCodec;
    private final UserService userService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(SessionRegistry sessionRegistry,
                                WsFrameCodec frameCodec,
                                UserService userService,
                                ChatService chatService,
                                ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.frameCodec = frameCodec;
        this.userService = userService;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = attribute(session, JwtHandshakeInterceptor.ATTRIBUTE_USER_ID);
        String username = attribute(session, JwtHandshakeInterceptor.ATTRIBUTE_USERNAME);
        if (userId == null) {
            closeQuietly(session, new CloseStatus(4401, "unauthorized"));
            return;
        }
        if (sessionRegistry.isShuttingDown()) {
            closeQuietly(session, new CloseStatus(1001, "server shutdown"));
            return;
        }
        // 包装成并发安全会话：广播线程与业务线程可能同时向同一会话发送
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES);
        SessionRegistry.ClientSession previous = sessionRegistry.register(userId, username, concurrentSession);
        if (previous != null) {
            // 同账号重复登录：旧连接先收 system 提示再关闭（契约 FR / EV-FUNC-15）
            sessionRegistry.send(previous, frameCodec.systemFrame("您的账号已在其他位置登录，当前连接已断开"));
            sessionRegistry.close(previous, new CloseStatus(1001, "duplicate login"));
            log.info("用户 {} 重复登录，旧连接被踢下线", userId);
        }
        log.info("WebSocket 连接建立: userId={}, sessionId={}", userId, session.getId());
        // 1) 广播全量在线列表（含自己上线）2) 补发离线私聊
        userService.handleUserOnline(userId);
        chatService.replayOfflineMessages(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Optional<SessionRegistry.ClientSession> clientSession = sessionRegistry.findBySessionId(session.getId());
        if (clientSession.isEmpty()) {
            closeQuietly(session, new CloseStatus(4401, "unauthorized"));
            return;
        }
        SessionRegistry.ClientSession client = clientSession.get();
        sessionRegistry.touch(session.getId());
        String payload = message.getPayload();
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path(FIELD_TYPE).asText("");
            switch (type) {
                case TYPE_PING -> sessionRegistry.send(client, frameCodec.pongFrame(System.currentTimeMillis()));
                case TYPE_CHAT -> handleChat(client, root);
                default -> sessionRegistry.send(client, frameCodec.errorFrame(
                        ErrorCodes.PROTOCOL_ERROR, "不支持的消息类型: " + type));
            }
        } catch (JsonProcessingException e) {
            sessionRegistry.send(client, frameCodec.errorFrame(ErrorCodes.PROTOCOL_ERROR, "消息不是合法的 JSON"));
        } catch (BusinessException e) {
            // 业务错误（1005/1008/1009 等）回 error 帧，连接保持可用（EV-SEC-05 / EV-SEC-11）
            sessionRegistry.send(client, frameCodec.errorFrame(e.code(), e.getMessage()));
        } catch (Exception e) {
            log.error("处理 WebSocket 消息异常: userId={}", client.userId(), e);
            sessionRegistry.send(client, frameCodec.errorFrame(ErrorCodes.INTERNAL_ERROR, "服务器内部错误"));
        }
    }

    /**
     * 处理 chat 帧。安全要点：消息里的 from/sender/username 等字段一律不读取，
     * 发送者以握手阶段解析出的 JWT 身份为准（SEC-04）。
     */
    private void handleChat(SessionRegistry.ClientSession client, JsonNode root) {
        Long receiverId = null;
        JsonNode toNode = root.get(FIELD_TO);
        if (toNode != null && !toNode.isNull()) {
            if (!toNode.canConvertToLong()) {
                throw new BusinessException(ErrorCodes.PROTOCOL_ERROR, "to 字段必须是用户 id 数字或 null");
            }
            receiverId = toNode.asLong();
        }
        JsonNode contentNode = root.get(FIELD_CONTENT);
        if (contentNode == null || !contentNode.isTextual()) {
            throw new BusinessException(ErrorCodes.PROTOCOL_ERROR, "content 字段必须是非空字符串");
        }
        chatService.send(client.userId(), receiverId, contentNode.asText());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        OptionalLong closedUserId = sessionRegistry.unregister(session.getId());
        if (closedUserId.isPresent() && !sessionRegistry.isShuttingDown()) {
            userService.handleUserOffline(closedUserId.getAsLong());
        }
        log.info("WebSocket 连接关闭: sessionId={}, code={}, reason={}",
                session.getId(), status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常: sessionId={}, reason={}", session.getId(), exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T attribute(WebSocketSession session, String name) {
        return (T) session.getAttributes().get(name);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception e) {
            log.debug("关闭 WebSocket 失败: sessionId={}, reason={}", session.getId(), e.getMessage());
        }
    }
}
