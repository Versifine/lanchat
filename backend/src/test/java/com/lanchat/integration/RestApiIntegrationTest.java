package com.lanchat.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanchat.infrastructure.config.LanChatProperties;
import com.lanchat.support.IntegrationTestSupport;
import com.lanchat.support.RestClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * REST 接口集成测试（@SpringBootTest + 真实 Tomcat + TestRestTemplate，随机端口）。
 *
 * <p>覆盖：EV-FUNC-01~07/11/12；EV-SEC-01/02/06/07/08/10/12。
 * 使用临时 SQLite 库与临时上传目录（见 {@link IntegrationTestSupport}），不污染正式数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestApiIntegrationTest {

    private static final IntegrationTestSupport.Env ENV = IntegrationTestSupport.createEnv();
    private static final String PASSWORD = "QaPass123!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.registerProperties(registry, ENV, "rest-it");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LanChatProperties properties;

    private RestClient api;

    @BeforeEach
    void setUp() {
        api = new RestClient(rest);
    }

    // ================================================================ 功能用例

    @Test
    void evFunc01_registerShouldReturnTokenAndPersistPbkdf2Hash() {
        String username = uniqueUsername();

        RestClient.ApiResponse response = api.postJson("/api/auth/register",
                Map.of("username", username, "password", PASSWORD, "nickname", "集成测试用户"), null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode json = response.json();
        assertThat(json.path("token").asText()).isNotBlank();
        assertThat(json.path("user").path("username").asText()).isEqualTo(username);
        assertThat(json.path("user").path("nickname").asText()).isEqualTo("集成测试用户");
        long userId = json.path("user").path("id").asLong();

        // 库中密码必须是 PBKDF2 格式，且不等于明文（EV-SEC-01）
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT password_hash, salt FROM users WHERE id = ?", userId);
        String passwordHash = (String) row.get("password_hash");
        assertThat(passwordHash)
                .matches("^pbkdf2\\$120000\\$[0-9a-f]{32}\\$[0-9a-f]{64}$")
                .doesNotContain(PASSWORD)
                .doesNotContain("QaPass");
        assertThat(row.get("salt")).isEqualTo(passwordHash.split("\\$")[2]);

        // 注册即登录：token 可解析出 userId（EV-FUNC-04 的一部分）
        String token = json.path("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        assertThatCode(() -> api.get("/api/auth/me", token)).doesNotThrowAnyException();
        assertThat(api.get("/api/auth/me", token).json().path("id").asLong()).isEqualTo(userId);
    }

    @Test
    void evFunc02_duplicateUsernameShouldReturn409And1002() {
        String username = uniqueUsername();
        api.register(username, PASSWORD);

        RestClient.ApiResponse response = api.postJson("/api/auth/register",
                Map.of("username", username, "password", PASSWORD), null);

        assertError(response, 409, 1002);
    }

    @Test
    void evFunc03_invalidParametersShouldReturn400And1004() {
        List<Map<String, Object>> invalidRegistrations = List.of(
                Map.of("username", "ab", "password", "abc123"),                 // 用户名 2 位
                Map.of("username", validUsername(), "password", "12345"),       // 密码 5 位
                Map.of("username", "bad name", "password", "abc123"),           // 用户名含空格
                Map.of("username", validUsername(), "password", "abc123",
                        "nickname", "昵".repeat(21)),                           // 昵称超长
                Map.of());                                                      // 全缺失
        for (Map<String, Object> body : invalidRegistrations) {
            assertError(api.postJson("/api/auth/register", body, null), 400, 1004);
        }

        // 非法 JSON → 400/1004
        assertError(api.postRawJson("/api/auth/register", "{bad json", null), 400, 1004);
        // 登录参数为空 → 400/1004
        assertError(api.postJson("/api/auth/login", Map.of("username", "", "password", ""), null),
                400, 1004);
    }

    @Test
    void evFunc04_loginShouldReturnTokenThatResolvesToUserId() {
        String username = uniqueUsername();
        RestClient.AuthInfo registered = api.register(username, PASSWORD);

        RestClient.ApiResponse response = api.postJson("/api/auth/login",
                Map.of("username", username, "password", PASSWORD), null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode json = response.json();
        assertThat(json.path("user").path("id").asLong()).isEqualTo(registered.id());
        String token = json.path("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(api.get("/api/auth/me", token).json().path("username").asText())
                .isEqualTo(username);
    }

    @Test
    void evFunc05_wrongPasswordShouldReturn401And1003() {
        String username = uniqueUsername();
        api.register(username, PASSWORD);

        RestClient.ApiResponse response = api.postJson("/api/auth/login",
                Map.of("username", username, "password", "WrongPass999!"), null);

        assertError(response, 401, 1003);
    }

    @Test
    void evFunc06_unauthenticatedAccessShouldReturn401And1001() {
        assertError(api.get("/api/users", null), 401, 1001);
        assertError(api.get("/api/auth/me", null), 401, 1001);
        assertError(api.get("/api/messages/group", null), 401, 1001);
        assertError(api.get("/api/users", "garbage-token"), 401, 1001);
        assertError(api.get("/api/users", "Bearer-not-used"), 401, 1001);
    }

    @Test
    void evSec02_tamperedAndExpiredTokensShouldReturn401() {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        String token = user.token();
        String[] parts = token.split("\\.");
        char first = parts[1].charAt(0);
        String tamperedPayload = (first == 'a' ? "b" : "a") + parts[1].substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        assertError(api.get("/api/users", tampered), 401, 1001);

        String expired = Jwts.builder()
                .subject(Long.toString(user.id()))
                .claim("username", user.username())
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 7_200_000L))
                .expiration(new java.util.Date(System.currentTimeMillis() - 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertError(api.get("/api/users", expired), 401, 1001);
    }

    @Test
    void evFunc07_userListShouldReturnAllUsersWithOnlineStatus() {
        RestClient.AuthInfo first = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo second = api.register(uniqueUsername(), PASSWORD);

        RestClient.ApiResponse response = api.get("/api/users", first.token());

        assertThat(response.status()).isEqualTo(200);
        JsonNode users = response.json();
        assertThat(users.isArray()).isTrue();
        JsonNode firstNode = findUser(users, first.id());
        JsonNode secondNode = findUser(users, second.id());
        assertThat(firstNode.path("username").asText()).isEqualTo(first.username());
        // 没有建立 WebSocket 连接 → 离线
        assertThat(firstNode.path("online").asBoolean()).isFalse();
        assertThat(secondNode.path("online").asBoolean()).isFalse();
    }

    @Test
    void evFunc11_historyEndpointsShouldReturnAscendingLatestMessages() {
        RestClient.AuthInfo alice = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo bob = api.register(uniqueUsername(), PASSWORD);
        RestClient.AuthInfo carol = api.register(uniqueUsername(), PASSWORD);

        // 210 条群聊消息（验证默认 50 条、最大 200 条上限）
        for (int i = 0; i < 210; i++) {
            jdbc.update("INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered)"
                            + " VALUES (?, NULL, ?, ?, 1)",
                    alice.id(), String.format("g%03d", i), 1_000L + i);
        }
        // 私聊：alice <-> bob 双向 5 条 + alice -> carol 2 条（不应出现在 alice/bob 历史中）
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered)"
                    + " VALUES (?, ?, ?, ?, 1)",
                    alice.id(), bob.id(), "a2b-" + i, 10_000L + i);
        }
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered)"
                    + " VALUES (?, ?, ?, ?, 1)",
                    bob.id(), alice.id(), "b2a-" + i, 20_000L + i);
        }
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered)"
                    + " VALUES (?, ?, ?, ?, 1)",
                    alice.id(), carol.id(), "a2c-" + i, 30_000L + i);
        }

        JsonNode defaultGroup = api.get("/api/messages/group", alice.token()).json();
        assertThat(defaultGroup).hasSize(50);
        assertThat(defaultGroup.get(0).path("content").asText()).isEqualTo("g160");
        assertThat(defaultGroup.get(49).path("content").asText()).isEqualTo("g209");
        assertAscending(defaultGroup);
        assertThat(defaultGroup.get(0).path("receiverId").isNull()).isTrue();
        assertThat(defaultGroup.get(0).path("sender").path("username").asText())
                .isEqualTo(alice.username());

        JsonNode limited = api.get("/api/messages/group?limit=10", alice.token()).json();
        assertThat(limited).hasSize(10);
        assertThat(limited.get(9).path("content").asText()).isEqualTo("g209");
        assertAscending(limited);

        // limit 上限 200（契约：最大 200）
        assertThat(api.get("/api/messages/group?limit=999", alice.token()).json()).hasSize(200);

        JsonNode privateHistory = api.get("/api/messages/private/" + bob.id(), alice.token()).json();
        assertThat(privateHistory).hasSize(5);
        assertAscending(privateHistory);
        assertThat(privateHistory).allSatisfy(message -> {
            long sender = message.path("sender").path("id").asLong();
            long receiver = message.path("receiverId").asLong();
            assertThat(List.of(sender, receiver)).containsExactlyInAnyOrder(alice.id(), bob.id());
        });
        assertThat(privateHistory).extracting(node -> node.path("content").asText())
                .doesNotContain("a2c-0", "a2c-1");

        assertError(api.get("/api/messages/private/999999", alice.token()), 404, 1005);
        assertError(api.get("/api/messages/private/" + alice.id(), alice.token()), 400, 1004);
        assertError(api.get("/api/messages/private/abc", alice.token()), 400, 1004);
    }

    @Test
    void evFunc12_fileUploadDownloadRoundTripShouldPreserveSha256() throws Exception {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        byte[] content = new byte[1024 * 1024];
        new Random(7).nextBytes(content);
        String expectedSha256 = sha256Hex(content);

        RestClient.ApiResponse upload = api.upload(user.token(), "roundtrip.bin", content, null);

        assertThat(upload.status()).isEqualTo(200);
        JsonNode file = upload.json().path("file");
        long fileId = file.path("id").asLong();
        assertThat(file.path("originalName").asText()).isEqualTo("roundtrip.bin");
        assertThat(file.path("size").asLong()).isEqualTo(content.length);
        assertThat(file.path("sha256").asText()).isEqualTo(expectedSha256);
        assertThat(file.path("downloadUrl").asText()).isEqualTo("/api/files/" + fileId + "/download");

        // 元数据接口
        JsonNode metadata = api.get("/api/files/" + fileId, user.token()).json();
        assertThat(metadata.path("sha256").asText()).isEqualTo(expectedSha256);
        assertThat(metadata.path("id").asLong()).isEqualTo(fileId);

        // 下载（Authorization: Bearer）字节一致
        RestClient.BytesResponse downloaded = api.download(
                "/api/files/" + fileId + "/download", user.token());
        assertThat(downloaded.status()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);
        assertThat(downloaded.headers().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains("roundtrip.bin");

        // 实现说明：下载接口兼容 ?token= 查询参数
        String tokenInQuery = URLEncoder.encode(user.token(), StandardCharsets.UTF_8);
        RestClient.BytesResponse queryDownload = api.download(
                "/api/files/" + fileId + "/download?token=" + tokenInQuery, null);
        assertThat(queryDownload.status()).isEqualTo(200);
        assertThat(queryDownload.body()).isEqualTo(content);
    }

    // ================================================================ 安全用例

    @Test
    void evSec06_oversizedUploadShouldReturn413And1006() {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        byte[] tooLarge = new byte[21 * 1024 * 1024];

        RestClient.ApiResponse response = api.upload(user.token(), "big.bin", tooLarge, null);

        assertError(response, 413, 1006);
    }

    @Test
    void evSec07_pathTraversalFilenameShouldBeSanitizedAndConfined() throws IOException {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        List<String> maliciousNames = List.of("../../evil.sh", "..\\..\\evil.sh", "/etc/passwd");
        List<String> expectedNames = List.of("evil.sh", "evil.sh", "passwd");

        for (int i = 0; i < maliciousNames.size(); i++) {
            RestClient.ApiResponse response = api.upload(
                    user.token(), maliciousNames.get(i), ("payload-" + i).getBytes(), null);

            assertThat(response.status()).as("上传应成功但文件名被清洗: %s", maliciousNames.get(i))
                    .isEqualTo(200);
            assertThat(response.json().path("file").path("originalName").asText())
                    .isEqualTo(expectedNames.get(i));
        }

        // 落盘名必须是服务端生成的安全名，目录内不出现原始恶意文件名
        assertThat(ENV.uploadDir()).isDirectory();
        try (Stream<Path> files = Files.list(ENV.uploadDir())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .isNotEmpty()
                    .allMatch(name -> name.matches("^[0-9a-f]{32}(\\.[a-z0-9]+)?$"));
        }
        try (Stream<Path> walked = Files.walk(ENV.root())) {
            assertThat(walked.map(path -> path.getFileName().toString()).toList())
                    .doesNotContain("evil.sh", "passwd", "..");
        }
        // 上传目录之外不存在被写入的恶意文件
        assertThat(ENV.root().resolve("evil.sh")).doesNotExist();
    }

    @Test
    void evSec08_sqlInjectionShouldNotBypassAuthentication() {
        // 注入式用户名注册：被用户名格式校验拦截（400/1004）
        assertError(api.postJson("/api/auth/register",
                Map.of("username", "a' OR '1'='1", "password", "abc123"), null), 400, 1004);
        // 注入式用户名登录：认证失败 401/1003，不能内部错误
        assertError(api.postJson("/api/auth/login",
                Map.of("username", "a' OR '1'='1", "password", "abc123"), null), 401, 1003);
        assertError(api.postJson("/api/auth/login",
                Map.of("username", "admin'--", "password", "abc123"), null), 401, 1003);

        // 正常用户注册/登录不受影响
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);
        assertThat(api.get("/api/users", user.token()).status()).isEqualTo(200);
        // 消息内容里的 SQL 片段应作为纯文本存储，不产生注入
        jdbc.update("INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered)"
                        + " VALUES (?, NULL, ?, ?, 1)",
                user.id(), "'; DROP TABLE users;--", 1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(1) FROM users", Long.class)).isPositive();
    }

    @Test
    void evSec10_loginRateLimitShouldReturn429AfterFiveFailures() {
        String username = uniqueUsername();
        api.register(username, PASSWORD);

        for (int i = 1; i <= 5; i++) {
            RestClient.ApiResponse response = api.postJson("/api/auth/login",
                    Map.of("username", username, "password", "WrongPass" + i + "!"), null);
            assertThat(response.status()).as("第 %d 次错误密码", i).isEqualTo(401);
            assertThat(response.json().path("code").asInt()).isEqualTo(1003);
        }

        RestClient.ApiResponse locked = api.postJson("/api/auth/login",
                Map.of("username", username, "password", "WrongPass6!"), null);

        assertError(locked, 429, 1009);
        // 正确密码也处于锁定期（锁定按用户名 1 分钟）
        assertError(api.postJson("/api/auth/login",
                Map.of("username", username, "password", PASSWORD), null), 429, 1009);
    }

    @Test
    void evSec12_logsShouldNotContainPlaintextPasswordOrFullToken() {
        Logger appLogger = (Logger) LoggerFactory.getLogger("com.lanchat");
        Level previousLevel = appLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        appLogger.setLevel(Level.DEBUG);
        appLogger.addAppender(appender);
        try {
            String username = uniqueUsername();
            String secretPassword = "LogLeak-Secret-42!";
            RestClient.AuthInfo user = api.register(username, secretPassword);
            api.postJson("/api/auth/login", Map.of("username", username, "password", secretPassword), null);
            api.postJson("/api/auth/login", Map.of("username", username, "password", "wrong"), null);
            api.get("/api/users", "invalid-token-for-log-test");
            api.upload(user.token(), "log.txt", "log-test".getBytes(), null);

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));

            assertThat(logs).as("日志不得包含明文密码").doesNotContain(secretPassword);
            assertThat(logs).as("日志不得包含完整 token").doesNotContain(user.token());
            assertThat(logs).as("日志不应包含 Bearer 明文").doesNotContain("Bearer " + user.token());
        } finally {
            appLogger.detachAppender(appender);
            appLogger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void unknownEndpointAndMalformedRequestShouldReturnContractErrors() {
        RestClient.AuthInfo user = api.register(uniqueUsername(), PASSWORD);

        assertError(api.get("/api/does-not-exist", user.token()), 404, 1004);
        assertThat(api.get("/api/messages/group?limit=abc", user.token()).status()).isEqualTo(400);
    }

    // ================================================================ 工具方法

    private static void assertError(RestClient.ApiResponse response, int expectedStatus, int expectedCode) {
        assertThat(response.status()).isEqualTo(expectedStatus);
        JsonNode json = response.json();
        assertThat(json.path("code").asInt()).isEqualTo(expectedCode);
        assertThat(json.path("message").asText()).isNotBlank();
    }

    private static JsonNode findUser(JsonNode users, long userId) {
        for (JsonNode user : users) {
            if (user.path("id").asLong() == userId) {
                return user;
            }
        }
        throw new AssertionError("用户列表中找不到 userId=" + userId);
    }

    private static void assertAscending(JsonNode messages) {
        long previous = Long.MIN_VALUE;
        for (JsonNode message : messages) {
            long createdAt = message.path("createdAt").asLong();
            assertThat(createdAt).isGreaterThanOrEqualTo(previous);
            previous = createdAt;
        }
    }

    private static String uniqueUsername() {
        return "qa_" + Long.toString(System.nanoTime(), 36) + SEQUENCE.getAndIncrement();
    }

    private static String validUsername() {
        return uniqueUsername();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
