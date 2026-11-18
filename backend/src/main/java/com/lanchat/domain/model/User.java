package com.lanchat.domain.model;

/**
 * 用户公开信息（不含任何密码材料）。
 *
 * <p>对应契约（docs/api.md）中的 user / sender 对象：id、username、nickname。</p>
 */
public record User(long id, String username, String nickname) {
}
