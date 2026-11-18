package com.lanchat.domain.port;

import com.lanchat.domain.model.Message;

import java.util.List;

/**
 * 消息仓储端口。实现位于 {@code adapter/out/persistence}（JdbcTemplate + PreparedStatement 参数化）。
 */
public interface MessageRepository {

    /**
     * 插入消息，返回自增主键。
     *
     * @param receiverId 接收方用户 id；null 表示群聊
     * @param delivered  私聊是否已投递（接收方在线为 true；群聊固定 true）
     */
    long insert(long senderId, Long receiverId, String content, long createdAt, boolean delivered);

    /** 最近 limit 条群聊消息，按时间升序返回。 */
    List<Message> findGroupHistory(int limit);

    /** 与 peerId 的双向私聊最近 limit 条，按时间升序返回。 */
    List<Message> findPrivateHistory(long userId, long peerId, int limit);

    /**
     * 原子领取（并标记已投递）某用户全部未投递私聊，按 id 升序返回。
     *
     * <p>“查询未投递 + 标记已投递”必须在同一事务内完成，避免重复登录/重连时重复补发。</p>
     */
    List<Message> claimUndelivered(long receiverId);
}
