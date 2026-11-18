package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 用户仓储测试（临时 SQLite + schema.sql）：插入/查询、username 唯一约束兜底（EV-FUNC-01/02、EV-SEC-01）。
 */
class JdbcUserRepositoryTest extends SqliteRepositoryTestSupport {

    private JdbcUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcUserRepository(jdbcTemplate);
    }

    @Test
    void insertShouldPersistUserAndBeQueryable() {
        long id = repository.insert("alice", "爱丽丝", validPasswordHash(), 1763432000123L);

        assertThat(id).isPositive();
        Optional<UserAccount> account = repository.findByUsername("alice");
        assertThat(account).isPresent();
        assertThat(account.orElseThrow().id()).isEqualTo(id);
        assertThat(account.orElseThrow().nickname()).isEqualTo("爱丽丝");
        assertThat(account.orElseThrow().passwordHash()).isEqualTo(validPasswordHash());
        assertThat(account.orElseThrow().createdAt()).isEqualTo(1763432000123L);
        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.findPublicById(id)).hasValue(new User(id, "alice", "爱丽丝"));
        assertThat(repository.existsByUsername("alice")).isTrue();
        assertThat(repository.existsByUsername("bob")).isFalse();
    }

    @Test
    void saltColumnShouldBeExtractedFromPasswordHash() {
        long id = repository.insert("alice", "爱丽丝", validPasswordHash(), 1L);

        String salt = jdbcTemplate.queryForObject(
                "SELECT salt FROM users WHERE id = ?", String.class, id);

        assertThat(salt).isEqualTo(validPasswordHash().split("\\$")[2]);
    }

    @Test
    void duplicateUsernameShouldBeRejectedByUniqueConstraint() {
        repository.insert("alice", "爱丽丝", validPasswordHash(), 1L);

        Throwable throwable = catchThrowable(() ->
                repository.insert("alice", "另一个爱丽丝", validPasswordHash(), 2L));

        // 唯一约束必须生效（EV-FUNC-02 的兜底）。
        // 已知实现问题（已报告 Lead）：sqlite-jdbc 的唯一约束异常没有被 Spring 翻译成
        // DuplicateKeyException，而是 UncategorizedSQLException，导致 JdbcUserRepository
        // 里 `catch (DuplicateKeyException)` 的 1002 兜底在 SQLite 下不生效，
        // 并发注册竞争时会返回 500/1012 而不是 409/1002。
        assertThat(throwable).isInstanceOf(DataAccessException.class);
        assertThat(countRows("users")).isEqualTo(1L);
    }

    @Test
    void usernameUniqueConstraintShouldAlsoGuardDirectSql() {
        repository.insert("alice", "爱丽丝", validPasswordHash(), 1L);

        Throwable throwable = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, salt, created_at) VALUES (?,?,?,?,?)",
                "alice", "dup", validPasswordHash(), "salt", 2L));

        assertThat(throwable).isInstanceOf(DataAccessException.class);
        assertThat(countRows("users")).isEqualTo(1L);
    }

    @Test
    void findAllShouldReturnUsersOrderedById() {
        insertUser("carol");
        insertUser("alice");
        insertUser("bob");

        List<User> users = repository.findAll();

        assertThat(users).extracting(User::username).containsExactly("carol", "alice", "bob");
    }

    @Test
    void missingUserShouldBeEmpty() {
        assertThat(repository.findByUsername("nobody")).isEmpty();
        assertThat(repository.findById(999L)).isEmpty();
        assertThat(repository.findPublicById(999L)).isEmpty();
    }
}
