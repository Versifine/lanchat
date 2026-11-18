package com.lanchat.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserPresence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 帧编解码单元测试：每个帧的字段必须与 docs/api.md 契约逐字段一致。
 */
class WsFrameCodecTest {

    /** 与 Spring Boot 全局配置一致：non_null，用于验证 record 上的 @JsonInclude(ALWAYS) 补偿生效。 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final WsFrameCodec codec = new WsFrameCodec(objectMapper);

    private static final User ALICE = new User(1L, "alice", "爱丽丝");
    private static final User BOB = new User(2L, "bob", "鲍勃");

    @Test
    void groupMessageFrameShouldExplicitlyIncludeNullReceiverId() throws Exception {
        Message message = new Message(10L, ALICE, null, "大家好", 1763432000123L, true);
        String json = codec.messageFrame(message, false);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("message");
        assertThat(node.path("id").asLong()).isEqualTo(10L);
        assertThat(node.path("sender").path("id").asLong()).isEqualTo(1L);
        assertThat(node.path("sender").path("username").asText()).isEqualTo("alice");
        assertThat(node.path("sender").path("nickname").asText()).isEqualTo("爱丽丝");
        assertThat(node.has("receiverId")).isTrue();
        assertThat(node.path("receiverId").isNull()).isTrue();
        assertThat(node.path("content").asText()).isEqualTo("大家好");
        assertThat(node.path("createdAt").asLong()).isEqualTo(1763432000123L);
        assertThat(node.path("offline").asBoolean()).isFalse();
    }

    @Test
    void privateMessageFrameShouldIncludeReceiverIdAndOfflineFlag() throws Exception {
        Message message = new Message(11L, BOB, 1L, "私聊", 1763432000456L, true);
        String json = codec.messageFrame(message, true);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("receiverId").asLong()).isEqualTo(1L);
        assertThat(node.path("offline").asBoolean()).isTrue();
        assertThat(node.path("sender").path("username").asText()).isEqualTo("bob");
    }

    @Test
    void userListFrameShouldContainAllUsersWithOnlineFlag() throws Exception {
        String json = codec.userListFrame(List.of(
                new UserPresence(1L, "alice", "爱丽丝", true),
                new UserPresence(2L, "bob", "鲍勃", false)));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("userList");
        assertThat(node.path("users")).hasSize(2);
        assertThat(node.path("users").get(0).path("online").asBoolean()).isTrue();
        assertThat(node.path("users").get(1).path("online").asBoolean()).isFalse();
        assertThat(node.path("users").get(1).path("nickname").asText()).isEqualTo("鲍勃");
    }

    @Test
    void fileFrameShouldMatchContractShape() throws Exception {
        ChatFile file = new ChatFile(7L, 1L, "report.pdf", "abc123.pdf", 1048576L,
                "f".repeat(64), 1763432000789L);
        String json = codec.fileFrame(file, ALICE, 2L);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("file");
        assertThat(node.path("file").path("id").asLong()).isEqualTo(7L);
        assertThat(node.path("file").path("originalName").asText()).isEqualTo("report.pdf");
        assertThat(node.path("file").path("size").asLong()).isEqualTo(1048576L);
        assertThat(node.path("file").path("sha256").asText()).hasSize(64);
        assertThat(node.path("file").path("downloadUrl").asText()).isEqualTo("/api/files/7/download");
        assertThat(node.path("sender").path("id").asLong()).isEqualTo(1L);
        assertThat(node.path("receiverId").asLong()).isEqualTo(2L);
    }

    @Test
    void pongSystemAndErrorFramesShouldMatchContractShape() throws Exception {
        JsonNode pong = objectMapper.readTree(codec.pongFrame(1763432000999L));
        assertThat(pong.path("type").asText()).isEqualTo("pong");
        assertThat(pong.path("timestamp").asLong()).isEqualTo(1763432000999L);

        JsonNode system = objectMapper.readTree(codec.systemFrame("服务端即将关闭"));
        assertThat(system.path("type").asText()).isEqualTo("system");
        assertThat(system.path("message").asText()).isEqualTo("服务端即将关闭");

        JsonNode error = objectMapper.readTree(codec.errorFrame(1008, "消息太长"));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(error.path("code").asInt()).isEqualTo(1008);
        assertThat(error.path("message").asText()).isEqualTo("消息太长");
    }
}
