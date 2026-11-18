package com.lanchat.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体（字段与 docs/api.md 契约一致）。
 */
@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "用户名", example = "alice")
        @NotBlank(message = "用户名不能为空")
        String username,

        @Schema(description = "密码", example = "abc123")
        @NotBlank(message = "密码不能为空")
        String password) {
}
