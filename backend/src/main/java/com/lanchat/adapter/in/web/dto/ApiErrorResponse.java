package com.lanchat.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一错误响应：{@code {"code":<int>,"message":"<中文说明>"}}（docs/api.md 第 1 节）。
 */
@Schema(description = "统一错误响应")
public record ApiErrorResponse(
        @Schema(description = "业务错误码", example = "1003") int code,
        @Schema(description = "中文错误说明", example = "用户名或密码错误") String message) {
}
