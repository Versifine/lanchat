package com.lanchat.adapter.out.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQLite 自增主键获取工具。
 *
 * <p>sqlite-jdbc 对 JDBC {@code getGeneratedKeys()} 的支持在不同版本/参数下行为不一致，
 * 因此这里在<b>同一个连接</b>内顺序执行 INSERT 与 {@code SELECT last_insert_rowid()}，
 * 既避免驱动兼容问题，也避免连接池串连接导致取错 id。</p>
 */
final class JdbcInsertHelper {

    private JdbcInsertHelper() {
    }

    /**
     * 执行 INSERT 并返回 SQLite 自增主键。
     *
     * @param sql  参数化 INSERT 语句（只能包含 {@code ?} 占位符，禁止拼接用户输入）
     * @param args 占位符参数
     */
    static long insert(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        Long generatedId = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            executeInsert(connection, sql, args);
            try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
            return null;
        });
        if (generatedId == null || generatedId <= 0) {
            throw new IllegalStateException("插入记录后未能获取 SQLite 自增主键");
        }
        return generatedId;
    }

    private static void executeInsert(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }
}
