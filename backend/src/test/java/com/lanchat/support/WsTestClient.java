package com.lanchat.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * WebSocket 集成测试客户端：基于 Spring {@link StandardWebSocketClient}，
 * 把收到的文本帧按原始字符串排队，测试用谓词异步等待目标帧。
 *
 * <p>{@link #connect(int, String)} 会等到服务端推送首个 userList 帧后返回，
 * 确保服务端已完成在线注册，避免“刚连上就发消息”的时序竞态。</p>
 */
public final class WsTestClient extends TextWebSocketHandler {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000L;

    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final BlockingQueue<CloseStatus> closeStatuses = new LinkedBlockingQueue<>();
    private volatile WebSocketSession session;

    private WsTestClient() {
    }

    /** 建立连接并等待首个 userList 帧（服务端注册在线完成的信号）。 */
    public static WsTestClient connect(int port, String token) throws Exception {
        WsTestClient client = new WsTestClient();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(client, url(port, token))
                .get(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        client.session = session;
        client.awaitType("userList", CONNECT_TIMEOUT_MILLIS);
        return client;
    }

    /** 构造 WS 地址（token 作为查询参数，与契约一致）。 */
    public static String url(int port, String token) {
        return "ws://localhost:" + port + "/ws/chat?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        frames.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeStatuses.add(status);
    }

    // ------------------------------------------------------------------ 发送

    public void sendRaw(String payload) {
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            throw new IllegalStateException("WebSocket 发送失败", e);
        }
    }

    public void sendChat(Long receiverId, String content) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat");
        frame.put("to", receiverId);
        frame.put("content", content);
        try {
            sendRaw(JSON.writeValueAsString(frame));
        } catch (Exception e) {
            throw new IllegalStateException("构造 chat 帧失败", e);
        }
    }

    public void sendPing() {
        sendRaw("{\"type\":\"ping\"}");
    }

    // ------------------------------------------------------------------ 接收

    /**
     * 等待第一个满足谓词的帧；超时抛出 AssertionError（由调用方决定是否需要 soft 版本）。
     */
    public JsonNode awaitFrame(Predicate<JsonNode> predicate, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new AssertionError("等待 WebSocket 帧超时（" + timeoutMillis + "ms）");
            }
            String raw;
            try {
                raw = frames.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待 WebSocket 帧被中断", e);
            }
            if (raw == null) {
                throw new AssertionError("等待 WebSocket 帧超时（" + timeoutMillis + "ms）");
            }
            JsonNode node = parse(raw);
            if (node != null && predicate.test(node)) {
                return node;
            }
        }
    }

    public JsonNode awaitType(String type, long timeoutMillis) {
        return awaitFrame(node -> type.equals(node.path("type").asText()), timeoutMillis);
    }

    /** 断言 timeout 内没有出现满足谓词的帧（用于“不应收到”的负向用例）。 */
    public void awaitNoFrame(Predicate<JsonNode> predicate, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            String raw;
            try {
                raw = frames.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待 WebSocket 帧被中断", e);
            }
            if (raw == null) {
                return;
            }
            JsonNode node = parse(raw);
            if (node != null && predicate.test(node)) {
                throw new AssertionError("不应收到该帧，但收到了: " + raw);
            }
        }
    }

    /** 清空当前积压帧（用于隔离前后断言）。 */
    public void drain() {
        frames.clear();
    }

    /** 等待连接关闭回调，返回关闭状态。 */
    public CloseStatus awaitClose(long timeoutMillis) {
        try {
            CloseStatus status = closeStatuses.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            if (status == null) {
                throw new AssertionError("等待 WebSocket 连接关闭超时（" + timeoutMillis + "ms）");
            }
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待 WebSocket 关闭被中断", e);
        }
    }

    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    public void close() {
        WebSocketSession current = session;
        if (current == null || !current.isOpen()) {
            return;
        }
        try {
            current.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // 测试清理阶段忽略关闭异常
        }
    }

    private static JsonNode parse(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
