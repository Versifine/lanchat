package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;
import com.lanchat.domain.port.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储的 SQLite 实现（JdbcTemplate + PreparedStatement 参数化，禁止拼接 SQL）。
 */
@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String ACCOUNT_COLUMNS = "id, username, nickname, password_hash, created_at";

    private static final RowMapper<UserAccount> ACCOUNT_ROW_MAPPER = (resultSet, rowNum) -> new UserAccount(
            resultSet.getLong("id"),
            resultSet.getString("username"),
            resultSet.getString("nickname"),
            resultSet.getString("password_hash"),
            resultSet.getLong("created_at"));

    private static final RowMapper<User> USER_ROW_MAPPER = (resultSet, rowNum) -> new User(
            resultSet.getLong("id"),
            resultSet.getString("username"),
            resultSet.getString("nickname"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> accounts = jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE username = ?",
                ACCOUNT_ROW_MAPPER,
                username);
        return accounts.stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        List<UserAccount> accounts = jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE id = ?",
                ACCOUNT_ROW_MAPPER,
                id);
        return accounts.stream().findFirst();
    }

    @Override
    public Optional<User> findPublicById(long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, username, nickname FROM users WHERE id = ?",
                USER_ROW_MAPPER,
                id);
        return users.stream().findFirst();
    }

    @Override
    public List<User> findAll() {
        return jdbcTemplate.query(
                "SELECT id, username, nickname FROM users ORDER BY id ASC",
                USER_ROW_MAPPER);
    }

    @Override
    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    @Override
    public long insert(String username, String nickname, String passwordHash, long createdAt) {
        // salt 列冗余保存盐值（便于人工审计密码格式），主哈希仍以 password_hash 的完整格式为准
        String salt = extractSalt(passwordHash);
        try {
            return JdbcInsertHelper.insert(jdbcTemplate,
                    "INSERT INTO users (username, nickname, password_hash, salt, created_at) VALUES (?, ?, ?, ?, ?)",
                    username,
                    nickname,
                    passwordHash,
                    salt,
                    createdAt);
        } catch (DuplicateKeyException e) {
            // 并发注册时唯一索引兜底，避免返回 500
            throw new BusinessException(ErrorCodes.USERNAME_TAKEN, "用户名已存在");
        }
    }

    /**
     * 从 {@code pbkdf2$迭代次数$盐hex$哈希hex} 中提取盐 hex，写入 users.salt 列。
     */
    private static String extractSalt(String passwordHash) {
        String[] parts = passwordHash.split("\\$");
        return parts.length >= 3 ? parts[2] : "";
    }
}
