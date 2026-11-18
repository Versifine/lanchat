package com.lanchat.domain.model;

/**
 * 聊天消息。
 *
 * <p>receiverId 为 null 表示群聊，非 null 表示私聊。</p>
 * <p>delivered 只对私聊有意义：0 = 接收方离线、待登录后补发；1 = 已投递（群聊消息固定为 1）。
 * 补发完成后由仓储原子地置为 1，保证同一条私聊不会被重复补发。</p>
 */
public record Message(long id, User sender, Long receiverId, String content, long createdAt, boolean delivered) {

    /** 是否群聊消息。 */
    public boolean isGroup() {
        return receiverId == null;
    }
}
