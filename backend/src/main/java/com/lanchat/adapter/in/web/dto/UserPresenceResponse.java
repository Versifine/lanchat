package com.lanchat.adapter.in.web.dto;

import com.lanchat.domain.model.UserPresence;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户 + 在线状态，对应契约 GET /api/users 与 WS userList 中的用户对象：
 * {@code {id, username, nickname, online}}。
 */
@Schema(description = "用户及在线状态")
public record UserPresenceResponse(
        @Schema(description = "用户 id") long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "是否在线（来自 WebSocket 在线表）") boolean online) {

    public static UserPresenceResponse from(UserPresence presence) {
        return new UserPresenceResponse(presence.id(), presence.username(), presence.nickname(), presence.online());
    }
}
