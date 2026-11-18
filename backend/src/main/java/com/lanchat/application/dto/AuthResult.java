package com.lanchat.application.dto;

/**
 * 认证结果：JWT + 当前用户信息（注册/登录共用，对应契约返回结构）。
 */
public record AuthResult(String token, UserView user) {
}
