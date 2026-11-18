package com.lanchat.domain.model;

/**
 * 从 JWT 解析出的当前登录用户身份。
 *
 * <p>安全规则（SEC-04）：消息/文件的发送者身份只从 JWT 解析，客户端在请求体或
 * WebSocket 消息里伪造的 from / sender 字段一律忽略。</p>
 */
public record AuthUser(long id, String username) {
}
