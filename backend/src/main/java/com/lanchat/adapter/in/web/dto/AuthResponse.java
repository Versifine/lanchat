package com.lanchat.adapter.in.web.dto;

import com.lanchat.application.dto.AuthResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 注册/登录成功响应：{@code {"token":"<JWT>","user":{"id","username","nickname"}}}。
 */
@Schema(description = "认证成功响应")
public record AuthResponse(
        @Schema(description = "JWT，后续请求放入 Authorization: Bearer <token>") String token,
        @Schema(description = "用户信息") UserResponse user) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), UserResponse.from(result.user()));
    }
}
