package com.lanchat.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserPresence;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket 帧编解码：把领域对象转换为 {@code docs/api.md} 定义的 JSON 帧。
 *
 * <p>本类只做协议转换，不含业务逻辑。注意全局 Jackson 配置为
 * {@code default-property-inclusion: non_null}，而契约要求群聊消息必须显式输出
 * {@code "receiverId": null}，因此各帧 record 统一标注
 * {@code @JsonInclude(ALWAYS)}，确保字段一个不少（前后端契约逐字段一致）。</p>
 */
@Component
public class WsFrameCodec {

    private final ObjectMapper objectMapper;

    public WsFrameCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 消息推送帧（群聊广播、私聊单播、离线补发共用；offline=true 表示离线补发）。 */
    public String messageFrame(Message message, boolean offline) {
        return write(new MessageFrame(
                "message",
                message.id(),
                WsUser.from(message.sender()),
                message.receiverId(),
                message.content(),
                message.createdAt(),
                offline));
    }

    /** 全量在线用户列表帧。 */
    public String userListFrame(List<UserPresence> users) {
        List<WsUserPresence> payload = users.stream().map(WsUserPresence::from).toList();
        return write(new UserListFrame("userList", payload));
    }

    /** 文件通知帧（发给接收方）。 */
    public String fileFrame(ChatFile file, User sender, long receiverId) {
        return write(new FileFrame("file", FilePayload.from(file), WsUser.from(sender), receiverId));
    }

    /** 系统提示帧（服务端公告/重复登录踢下线/心跳超时）。 */
    public String systemFrame(String message) {
        return write(new SystemFrame("system", message));
    }

    /** 心跳应答帧。 */
    public String pongFrame(long timestamp) {
        return write(new PongFrame("pong", timestamp));
    }

    /** 业务错误帧（如 1008 消息过长、1009 限流）。 */
    public String errorFrame(int code, String message) {
        return write(new ErrorFrame("error", code, message));
    }

    private String write(Object frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WebSocket 帧序列化失败", e);
        }
    }

    /** 服务端 → 客户端：message 帧。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageFrame(String type,
                               long id,
                               WsUser sender,
                               Long receiverId,
                               String content,
                               long createdAt,
                               boolean offline) {
    }

    /** 服务端 → 客户端：userList 帧中的用户对象（含在线状态）。 */
    public record WsUserPresence(long id, String username, String nickname, boolean online) {
        public static WsUserPresence from(UserPresence presence) {
            return new WsUserPresence(presence.id(), presence.username(), presence.nickname(), presence.online());
        }
    }

    /** 服务端 → 客户端：userList 帧。 */
    public record UserListFrame(String type, List<WsUserPresence> users) {
    }

    /** message/file 帧中的发送者对象（契约：sender:{id,username,nickname}）。 */
    public record WsUser(long id, String username, String nickname) {
        public static WsUser from(User user) {
            return new WsUser(user.id(), user.username(), user.nickname());
        }
    }

    /** 服务端 → 客户端：file 帧中的文件对象（与 REST file 对象同结构）。 */
    public record FilePayload(long id, String originalName, long size, String sha256, String downloadUrl) {
        public static FilePayload from(ChatFile file) {
            return new FilePayload(file.id(), file.originalName(), file.size(), file.sha256(),
                    "/api/files/" + file.id() + "/download");
        }
    }

    /** 服务端 → 客户端：file 帧。 */
    public record FileFrame(String type, FilePayload file, WsUser sender, long receiverId) {
    }

    /** 服务端 → 客户端：system 帧。 */
    public record SystemFrame(String type, String message) {
    }

    /** 服务端 → 客户端：pong 帧。 */
    public record PongFrame(String type, long timestamp) {
    }

    /** 服务端 → 客户端：error 帧。 */
    public record ErrorFrame(String type, int code, String message) {
    }
}
