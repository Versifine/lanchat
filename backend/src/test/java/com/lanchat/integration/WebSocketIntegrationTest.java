package com.lanchat.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanchat.support.IntegrationTestSupport;
import com.lanchat.support.RestClient;
import com.lanchat.support.TestAwait;
import com.lanchat.support.WsTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * WebSocket 集成测试（@SpringBootTest 随机端口 + Spring StandardWebSocketClient）。
 *
 * <p>覆盖：EV-FUNC-07~10/13/14/15；EV-SEC-03/04/05/09/11/12。
 * 使用临时 SQLite 库与临时上传目录，不污染正式数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    private static final IntegrationTestSupport.Env ENV = IntegrationTestSupport.createEnv();
    private static final String PASSWORD = "QaPass123!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.registerProperties(registry, ENV, "ws-it");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private RestClient api;
    private final List<WsTestClient> openedClients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        api = new RestClient(rest);
    }

    @AfterEach
    void tearDown() {
        openedClients.forEach(WsTestClient::close);
        openedClients.clear();
    }

    // ================================================================ 握手鉴权

    @Test
    void evSec03_handshakeWithInvalidOrMissingTokenShouldBeRejected() {
        Throwable forged = catchThrowable(() -> WsTestClient.connect(port, "forged.token.value"));
        assertThat(forged).isNotNull();
        assertThat(exceptionChain(forged)).contains("401");

        Throwable missing = catchThrowable(() -> WsTestClient.connect(port, ""));
        assertThat(missing).isNotNull();
        assertThat(exceptionChain(missing)).contains("401");
    }

    // ================================================================ 心跳

    @Test
    void evFunc14_pingShouldReceivePong() throws Exception {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        WsTestClient client = connect(user);

        client.sendPing();

        JsonNode pong = client.awaitType("pong", 5_000);
        assertThat(pong.path("timestamp").asLong()).isPositive();
    }

    // ================================================================ 在线列表

    @Test
    void evFunc07_userListShouldBroadcastOnlineChanges() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        WsTestClient aliceWs = connect(alice);
        WsTestClient bobWs = connect(bob);

        // A 应收到包含 B 在线的 userList 帧
        JsonNode onlineFrame = aliceWs.awaitFrame(
                node -> isType(node, "userList") && hasUserWithOnline(node, bob.id(), true), 5_000);
        assertThat(onlineFrame).isNotNull();

        // REST /api/users 的 online 字段与在线表一致
        TestAwait.until(Duration.ofSeconds(5), "REST 用户列表显示 Alice/Bob 均在线", () -> {
            JsonNode users = api.get("/api/users", alice.token()).json();
            return hasUserWithOnline(users, alice.id(), true) && hasUserWithOnline(users, bob.id(), true);
        });

        bobWs.close();

        JsonNode offlineFrame = aliceWs.awaitFrame(
                node -> isType(node, "userList") && hasUserWithOnline(node, bob.id(), false), 5_000);
        assertThat(offlineFrame).isNotNull();
    }

    // ================================================================ 群聊

    @Test
    void evFunc08_groupMessageShouldBroadcastToAllClientsInSameOrderAndPersist() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        WsTestClient aliceWs = connect(alice);
        WsTestClient bobWs = connect(bob);
        String tag = "group-" + System.nanoTime() + "-";

        List<Long> aliceIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            aliceWs.sendChat(null, tag + i);
        }
        for (int i = 0; i < 3; i++) {
            String expected = tag + i;
            JsonNode frame = aliceWs.awaitFrame(node -> isMessageWithContent(node, expected), 5_000);
            assertThat(frame.path("receiverId").isNull()).isTrue();
            assertThat(frame.path("sender").path("id").asLong()).isEqualTo(alice.id());
            aliceIds.add(frame.path("id").asLong());
        }
        List<Long> bobIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String expected = tag + i;
            JsonNode frame = bobWs.awaitFrame(node -> isMessageWithContent(node, expected), 5_000);
            bobIds.add(frame.path("id").asLong());
        }
        assertThat(bobIds).as("A/B 收到的消息 id 与顺序必须一致").isEqualTo(aliceIds);

        Long stored = jdbc.queryForObject(
                "SELECT COUNT(1) FROM messages WHERE receiver_id IS NULL AND content LIKE ?",
                Long.class, tag + "%");
        assertThat(stored).isEqualTo(3L);

        // 历史接口能看到这三条，且按时间升序
        JsonNode history = api.get("/api/messages/group?limit=3", alice.token()).json();
        assertThat(history).hasSize(3);
        assertThat(history.get(0).path("content").asText()).isEqualTo(tag + "0");
        assertThat(history.get(2).path("content").asText()).isEqualTo(tag + "2");
    }

    // ================================================================ 私聊

    @Test
    void evFunc09_onlinePrivateMessageShouldReachOnlySenderAndReceiver() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo carol = api.register(uniqueUsername(), PASSWORD);
        WsTestClient aliceWs = connect(alice);
        WsTestClient bobWs = connect(bob);
        WsTestClient carolWs = connect(carol);
        String tag = "private-online-" + System.nanoTime();

        aliceWs.sendChat(bob.id(), tag);

        JsonNode aliceEcho = aliceWs.awaitFrame(node -> isMessageWithContent(node, tag), 5_000);
        assertThat(aliceEcho.path("receiverId").asLong()).isEqualTo(bob.id());
        JsonNode bobEcho = bobWs.awaitFrame(node -> isMessageWithContent(node, tag), 5_000);
        assertThat(bobEcho.path("id").asLong()).isEqualTo(aliceEcho.path("id").asLong());
        // 第三方不应收到
        carolWs.awaitNoFrame(node -> isMessageWithContent(node, tag), 1_200);
    }

    @Test
    void evFunc10_offlinePrivateMessageShouldBeReplayedOnLogin() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        WsTestClient aliceWs = connect(alice);
        String tag = "private-offline-" + System.nanoTime();

        // Bob 尚未建立 WS（离线）
        aliceWs.sendChat(bob.id(), tag);
        JsonNode echo = aliceWs.awaitFrame(node -> isMessageWithContent(node, tag), 5_000);
        assertThat(echo.path("receiverId").asLong()).isEqualTo(bob.id());
        assertThat(echo.path("offline").asBoolean()).isFalse();

        // Bob 登录（建立 WS）后应收到 offline=true 的补发消息
        WsTestClient bobWs = connect(bob);
        JsonNode replay = bobWs.awaitFrame(node -> isMessageWithContent(node, tag), 5_000);
        assertThat(replay.path("offline").asBoolean()).isTrue();
        assertThat(replay.path("sender").path("id").asLong()).isEqualTo(alice.id());

        // 私聊历史可查
        JsonNode history = api.get("/api/messages/private/" + alice.id(), bob.token()).json();
        assertThat(history).extracting(node -> node.path("content").asText()).contains(tag);

        // 补发只发生一次：重连后不应再次收到同一条离线补发
        bobWs.close();
        WsTestClient bobAgain = connect(bob);
        bobAgain.awaitNoFrame(node -> isMessageWithContent(node, tag), 1_200);
    }

    // ================================================================ 重复登录 / 文件通知

    @Test
    void evFunc15_duplicateLoginShouldKickOldConnection() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        WsTestClient oldWs = connect(alice);
        WsTestClient newWs = connect(alice);

        JsonNode system = oldWs.awaitFrame(
                node -> isType(node, "system")
                        && node.path("message").asText().contains("其他位置登录"), 5_000);
        assertThat(system).isNotNull();
        CloseStatus closeStatus = oldWs.awaitClose(5_000);
        assertThat(closeStatus.getCode()).isEqualTo(1001);
        assertThat(oldWs.isOpen()).isFalse();

        // 新连接仍然有效
        newWs.sendPing();
        assertThat(newWs.awaitType("pong", 5_000).path("timestamp").asLong()).isPositive();
        assertThat(api.get("/api/users", alice.token()).json()).isNotNull();
        TestAwait.until(Duration.ofSeconds(5), "重复登录后新连接仍在线",
                () -> hasUserWithOnline(api.get("/api/users", alice.token()).json(), alice.id(), true));
    }

    @Test
    void evFunc13_fileUploadShouldNotifyReceiverOverWebSocket() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        WsTestClient bobWs = connect(bob);
        byte[] content = "file-notify-content".getBytes();

        RestClient.ApiResponse upload = api.upload(alice.token(), "notice.txt", content, bob.id());
        assertThat(upload.status()).isEqualTo(200);
        long fileId = upload.json().path("file").path("id").asLong();

        JsonNode fileFrame = bobWs.awaitFrame(
                node -> isType(node, "file") && node.path("file").path("id").asLong() == fileId, 5_000);
        assertThat(fileFrame.path("file").path("originalName").asText()).isEqualTo("notice.txt");
        assertThat(fileFrame.path("sender").path("id").asLong()).isEqualTo(alice.id());
        assertThat(fileFrame.path("receiverId").asLong()).isEqualTo(bob.id());
        assertThat(fileFrame.path("file").path("downloadUrl").asText())
                .isEqualTo("/api/files/" + fileId + "/download");

        // 接收方可下载且字节一致
        RestClient.BytesResponse download = api.download(
                "/api/files/" + fileId + "/download", bob.token());
        assertThat(download.status()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(content);
    }

    // ================================================================ 安全用例

    @Test
    void evSec04_forgedSenderFieldsShouldBeIgnored() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        WsTestClient aliceWs = connect(alice);
        String tag = "forge-" + System.nanoTime();

        aliceWs.sendRaw("{\"type\":\"chat\",\"to\":null,\"content\":\"" + tag
                + "\",\"sender\":{\"id\":" + bob.id() + "},\"from\":" + bob.id() + "}");

        JsonNode frame = aliceWs.awaitFrame(node -> isMessageWithContent(node, tag), 5_000);
        assertThat(frame.path("sender").path("id").asLong())
                .as("发送者必须来自 JWT 身份，而非消息体伪造字段")
                .isEqualTo(alice.id());
        Long forgedSender = jdbc.queryForObject(
                "SELECT sender_id FROM messages WHERE content = ?", Long.class, tag);
        assertThat(forgedSender).isEqualTo(alice.id());
    }

    @Test
    void evSec05_tooLongMessageShouldReturnError1008WithoutDisconnecting() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        WsTestClient client = connect(alice);
        String tooLong = "x".repeat(2_001);

        client.sendChat(null, tooLong);

        JsonNode error = client.awaitFrame(
                node -> isType(node, "error") && node.path("code").asInt() == 1008, 5_000);
        assertThat(error.path("message").asText()).isNotBlank();
        // 连接保持可用
        client.sendPing();
        assertThat(client.awaitType("pong", 5_000)).isNotNull();
        Long stored = jdbc.queryForObject(
                "SELECT COUNT(1) FROM messages WHERE content = ?", Long.class, tooLong);
        assertThat(stored).isZero();
    }

    @Test
    void evSec09_xssContentShouldBeStoredAndReturnedAsPlainTextJson() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        WsTestClient client = connect(alice);
        String xss = "<script>alert('xss')</script><img src=x onerror=alert(2)>";

        client.sendChat(null, xss);

        JsonNode frame = client.awaitFrame(node -> isMessageWithContent(node, xss), 5_000);
        assertThat(frame.path("content").asText()).isEqualTo(xss);

        JsonNode history = api.get("/api/messages/group?limit=1", alice.token()).json();
        assertThat(history.get(0).path("content").asText()).isEqualTo(xss);
        RestClient.ApiResponse raw = api.get("/api/messages/group?limit=1", alice.token());
        assertThat(raw.headers().getFirst("Content-Type")).contains("application/json");
    }

    @Test
    void evSec11_messageBurstShouldBeRateLimitedWithoutCrashingServer() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        WsTestClient client = connect(alice);
        String tag = "rate-spam-" + System.nanoTime() + "-";

        for (int i = 0; i < 60; i++) {
            client.sendChat(null, tag + i);
        }

        JsonNode error = client.awaitFrame(
                node -> isType(node, "error") && node.path("code").asInt() == 1009, 10_000);
        assertThat(error.path("message").asText()).isNotBlank();
        // 服务端未崩，连接仍可心跳
        client.sendPing();
        assertThat(client.awaitType("pong", 5_000)).isNotNull();
        Long stored = jdbc.queryForObject(
                "SELECT COUNT(1) FROM messages WHERE content LIKE ?", Long.class, tag + "%");
        assertThat(stored).isBetween(1L, 59L);
    }

    @Test
    void evSec12_webSocketTokenShouldNeverBeLogged() throws Exception {
        Logger appLogger = (Logger) LoggerFactory.getLogger("com.lanchat");
        Level previousLevel = appLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        appLogger.setLevel(Level.DEBUG);
        appLogger.addAppender(appender);
        try {
            RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
            WsTestClient client = connect(alice);
            client.sendChat(null, "log-check-" + System.nanoTime());
            catchThrowable(() -> WsTestClient.connect(port, "leaky-ws-token-xyz"));

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logs).doesNotContain(alice.token());
            assertThat(logs).doesNotContain("leaky-ws-token-xyz");
        } finally {
            appLogger.detachAppender(appender);
            appLogger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void protocolErrorsShouldReturnErrorFrame1011() throws Exception {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        WsTestClient client = connect(alice);

        client.sendRaw("this-is-not-json");
        assertThat(client.awaitFrame(
                node -> isType(node, "error") && node.path("code").asInt() == 1011, 5_000))
                .isNotNull();

        client.sendRaw("{\"type\":\"unknown-type\"}");
        assertThat(client.awaitFrame(
                node -> isType(node, "error") && node.path("code").asInt() == 1011, 5_000))
                .isNotNull();

        client.sendRaw("{\"type\":\"chat\",\"to\":\"not-a-number\",\"content\":\"x\"}");
        assertThat(client.awaitFrame(
                node -> isType(node, "error") && node.path("code").asInt() == 1011, 5_000))
                .isNotNull();

        client.sendPing();
        assertThat(client.awaitType("pong", 5_000)).isNotNull();
    }

    // ================================================================ 工具方法

    private WsTestClient connect(RestClient.AuthInfo user) throws Exception {
        WsTestClient client = WsTestClient.connect(port, user.token());
        openedClients.add(client);
        return client;
    }

    private static boolean isType(JsonNode node, String type) {
        return type.equals(node.path("type").asText());
    }

    private static boolean isMessageWithContent(JsonNode node, String content) {
        return isType(node, "message") && content.equals(node.path("content").asText());
    }

    private static boolean hasUserWithOnline(JsonNode frame, long userId, boolean online) {
        // WS userList 帧形如 {"type":"userList","users":[...]}；REST /api/users 直接返回数组
        JsonNode users = frame.isArray() ? frame : frame.path("users");
        for (JsonNode user : users) {
            if (user.path("id").asLong() == userId) {
                return user.path("online").asBoolean() == online;
            }
        }
        return false;
    }

    private static String exceptionChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            builder.append(current.getClass().getSimpleName())
                    .append(": ").append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }

    private static String uniqueUsername() {
        return "qa_" + Long.toString(System.nanoTime(), 36) + SEQUENCE.getAndIncrement();
    }
}
