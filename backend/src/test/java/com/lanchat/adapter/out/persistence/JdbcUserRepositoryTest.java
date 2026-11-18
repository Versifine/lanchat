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
    void duplicateUsernameShouldBeRejectedWithBusinessCode1002() {
        repository.insert("alice", "爱丽丝", validPasswordHash(), 1L);

        Throwable throwable = catchThrowable(() ->
                repository.insert("alice", "另一个爱丽丝", validPasswordHash(), 2L));

        // 唯一约束兜底：sqlite-jdbc 的约束冲突（errorCode=19）必须被翻译成业务错误 1002，
        // 否则并发注册竞争时输的一方会返回 500/1012（EV-FUNC-02 / SEC-03 的兜底路径）。
        assertThat(throwable).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) throwable).code()).isEqualTo(1002);
        assertThat(countRows("users")).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicateRegistrationShouldYieldOneSuccessAndOne1002() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Object> outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        try {
            for (int i = 0; i < 2; i++) {
                final int worker = i;
                pool.submit(() -> {
                    start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        repository.insert("race_user", "并发" + worker, validPasswordHash(), 10L + worker);
                        outcomes.add("success");
                    } catch (Throwable throwable) {
                        outcomes.add(throwable);
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        long successCount = outcomes.stream().filter("success"::equals).count();
        long conflictCount = outcomes.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .filter(error -> error.code() == 1002)
                .count();
        assertThat(successCount).as("并发注册成功数").isEqualTo(1);
        assertThat(conflictCount).as("并发注册冲突返回 1002 数").isEqualTo(1);
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
