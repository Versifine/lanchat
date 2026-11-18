package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;
import com.lanchat.domain.port.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 用户仓储的 SQLite 实现（JdbcTemplate + PreparedStatement 参数化，禁止拼接 SQL）。
 */
@Repository
public class JdbcUserRepository implements UserRepository {

    /** SQLite 的 SQLITE_CONSTRAINT 错误码（唯一约束等完整性约束失败时返回）。 */
    private static final int SQLITE_CONSTRAINT = 19;

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
            // MySQL/PostgreSQL/H2 等驱动可被 Spring 正确翻译成 DuplicateKeyException
            throw new BusinessException(ErrorCodes.USERNAME_TAKEN, "用户名已存在");
        } catch (DataAccessException e) {
            // sqlite-jdbc 的唯一约束异常 SQLState=null、errorCode=19(SQLITE_CONSTRAINT)，
            // Spring 无法翻译成 DuplicateKeyException（实际抛 UncategorizedSQLException），
            // 这里按驱动错误码/消息兜底识别，保证并发注册竞争时输的一方返回 409/1002 而不是 500/1012。
            if (isUniqueConstraintViolation(e)) {
                throw new BusinessException(ErrorCodes.USERNAME_TAKEN, "用户名已存在");
            }
            throw e;
        }
    }

    /**
     * 识别底层是否为唯一约束冲突（并发注册同一用户名的兜底路径）。
     *
     * <p>依次检查异常链上：SQLite errorCode=19（SQLITE_CONSTRAINT）、标准 SQLState 23xxx
     * （integrity constraint violation）、以及驱动消息中的 UNIQUE/CONSTRAINT 关键字。</p>
     */
    private static boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                // SQLite: 19 = SQLITE_CONSTRAINT（唯一约束冲突时 SQLState 为 null）
                if (sqlException.getErrorCode() == SQLITE_CONSTRAINT) {
                    return true;
                }
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("23")) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("UNIQUE") || normalized.contains("CONSTRAINT")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 从 {@code pbkdf2$迭代次数$盐hex$哈希hex} 中提取盐 hex，写入 users.salt 列。
     */
    private static String extractSalt(String passwordHash) {
        String[] parts = passwordHash.split("\\$");
        return parts.length >= 3 ? parts[2] : "";
    }
}
