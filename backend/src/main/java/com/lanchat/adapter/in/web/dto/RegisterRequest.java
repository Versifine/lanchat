package com.lanchat.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体（字段与 docs/api.md 契约一致）。
 *
 * <p>adapter 层先做一次格式校验（格式/长度/必填），应用层再做一次业务校验，双重保障。</p>
 */
@Schema(description = "注册请求")
public record RegisterRequest(
        @Schema(description = "用户名：3-20 位字母/数字/下划线", example = "alice")
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线")
        String username,

        @Schema(description = "密码：6-32 位", example = "abc123")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        String password,

        @Schema(description = "昵称：不超过 20 个字符", example = "爱丽丝")
        @Size(max = 20, message = "昵称长度不能超过 20 个字符")
        String nickname) {
}
