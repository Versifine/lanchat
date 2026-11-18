package com.lanchat.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 仓储测试基类：每个测试方法独立创建一个临时 SQLite 库，执行 classpath:schema.sql 建表，
 * 再基于真实的 JdbcTemplate 仓储实现做验证（不污染 data/lanchat.db）。
 */
abstract class SqliteRepositoryTestSupport {

    protected Path tempDir;
    protected SQLiteDataSource dataSource;
    protected JdbcTemplate jdbcTemplate;
    protected DataSourceTransactionManager transactionManager;

    @BeforeEach
    void initializeDatabase() throws Exception {
        tempDir = Files.createTempDirectory("lanchat-repo-test-");
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("lanchat-test.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
    }

    @AfterEach
    void disposeDatabase() throws Exception {
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (Exception ignored) {
                // 清理阶段尽力而为
            }
        }
        if (tempDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 忽略删除失败（临时目录由系统回收）
                }
            });
        }
    }

    /** 插入一个测试用户，返回自增 id。 */
    protected long insertUser(String username) {
        return new JdbcUserRepository(jdbcTemplate)
                .insert(username, username + "昵称", validPasswordHash(), System.currentTimeMillis());
    }

    /** 合法的 PBKDF2 格式哈希（仓储测试不校验密码，只验证格式与盐列提取）。 */
    protected static String validPasswordHash() {
        return "pbkdf2$120000$" + "0123456789abcdef0123456789abcdef" + "$" + "ab".repeat(32);
    }

    protected long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }
}
