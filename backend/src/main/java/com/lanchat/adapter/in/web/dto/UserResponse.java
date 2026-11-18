package com.lanchat.adapter.in.web.dto;

import com.lanchat.application.dto.UserView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户响应对象，对应契约中的 user 对象：{@code {id, username, nickname}}。
 */
@Schema(description = "用户信息")
public record UserResponse(
        @Schema(description = "用户 id") long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname) {

    public static UserResponse from(UserView view) {
        return new UserResponse(view.id(), view.username(), view.nickname());
    }
}
