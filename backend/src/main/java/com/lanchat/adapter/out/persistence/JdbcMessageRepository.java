package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.port.MessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 消息仓储的 SQLite 实现。
 *
 * <p>历史消息查询统一“先按时间倒序取最近 N 条，再反转为升序”，保证契约要求的升序返回；
 * 排序附带 id 兜底，避免同一毫秒内消息顺序不稳定。</p>
 */
@Repository
public class JdbcMessageRepository implements MessageRepository {

    private static final String SELECT_BASE = """
            SELECT m.id, m.sender_id, m.receiver_id, m.content, m.created_at, m.delivered,
                   u.username AS sender_username, u.nickname AS sender_nickname
            FROM messages m
            JOIN users u ON u.id = m.sender_id
            """;

    private static final RowMapper<Message> MESSAGE_ROW_MAPPER = (resultSet, rowNum) -> {
        User sender = new User(
                resultSet.getLong("sender_id"),
                resultSet.getString("sender_username"),
                resultSet.getString("sender_nickname"));
        long rawReceiverId = resultSet.getLong("receiver_id");
        Long receiverId = resultSet.wasNull() ? null : rawReceiverId;
        return new Message(
                resultSet.getLong("id"),
                sender,
                receiverId,
                resultSet.getString("content"),
                resultSet.getLong("created_at"),
                resultSet.getInt("delivered") != 0);
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcMessageRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public long insert(long senderId, Long receiverId, String content, long createdAt, boolean delivered) {
        return JdbcInsertHelper.insert(jdbcTemplate,
                "INSERT INTO messages (sender_id, receiver_id, content, created_at, delivered) VALUES (?, ?, ?, ?, ?)",
                senderId,
                receiverId,
                content,
                createdAt,
                delivered ? 1 : 0);
    }

    @Override
    public List<Message> findGroupHistory(int limit) {
        List<Message> descending = jdbcTemplate.query(
                SELECT_BASE + " WHERE m.receiver_id IS NULL ORDER BY m.created_at DESC, m.id DESC LIMIT ?",
                MESSAGE_ROW_MAPPER,
                limit);
        return ascending(descending);
    }

    @Override
    public List<Message> findPrivateHistory(long userId, long peerId, int limit) {
        List<Message> descending = jdbcTemplate.query(
                SELECT_BASE + """
                         WHERE (m.sender_id = ? AND m.receiver_id = ?)
                            OR (m.sender_id = ? AND m.receiver_id = ?)
                         ORDER BY m.created_at DESC, m.id DESC
                         LIMIT ?
                        """,
                MESSAGE_ROW_MAPPER,
                userId,
                peerId,
                peerId,
                userId,
                limit);
        return ascending(descending);
    }

    @Override
    public List<Message> claimUndelivered(long receiverId) {
        // 查询未投递 + 标记已投递必须同一事务，否则重复连接可能重复补发
        return Objects.requireNonNullElse(transactionTemplate.execute(status -> {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM messages WHERE receiver_id = ? AND delivered = 0 ORDER BY id ASC",
                    Long.class,
                    receiverId);
            if (ids.isEmpty()) {
                return List.<Message>of();
            }
            List<Object[]> batchArgs = ids.stream().map(id -> new Object[]{id}).toList();
            jdbcTemplate.batchUpdate("UPDATE messages SET delivered = 1 WHERE id = ?", batchArgs);
            // 占位符由 id 列表长度生成、参数化传入，不存在 SQL 注入
            String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
            return jdbcTemplate.query(
                    SELECT_BASE + " WHERE m.id IN (" + placeholders + ") ORDER BY m.id ASC",
                    MESSAGE_ROW_MAPPER,
                    ids.toArray());
        }), List.of());
    }

    private static List<Message> ascending(List<Message> descending) {
        List<Message> messages = new ArrayList<>(descending);
        Collections.reverse(messages);
        return messages;
    }
}
