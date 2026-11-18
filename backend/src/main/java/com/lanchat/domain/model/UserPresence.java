package com.lanchat.domain.model;

/**
 * 用户在线状态视图：用户基本信息 + 是否在线。
 *
 * <p>在线状态来自 WebSocket 适配器维护的在线会话表，不属于数据库持久化数据。</p>
 */
public record UserPresence(long id, String username, String nickname, boolean online) {

    public static UserPresence of(User user, boolean online) {
        return new UserPresence(user.id(), user.username(), user.nickname(), online);
    }
}
