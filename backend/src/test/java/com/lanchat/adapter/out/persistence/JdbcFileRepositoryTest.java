package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.model.ChatFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 文件元数据仓储测试（临时 SQLite + schema.sql）：插入/查询、stored_name 唯一约束（EV-FUNC-12）。
 */
class JdbcFileRepositoryTest extends SqliteRepositoryTestSupport {

    private JdbcFileRepository repository;
    private long uploaderId;

    @BeforeEach
    void setUp() {
        repository = new JdbcFileRepository(jdbcTemplate);
        uploaderId = insertUser("alice");
    }

    @Test
    void insertShouldPersistMetadataAndBeQueryable() {
        long id = repository.insert(uploaderId, "report.pdf", "uuid.pdf", 1048576L,
                "f".repeat(64), 1763432000123L);

        ChatFile file = repository.findById(id).orElseThrow();

        assertThat(file.id()).isEqualTo(id);
        assertThat(file.uploaderId()).isEqualTo(uploaderId);
        assertThat(file.originalName()).isEqualTo("report.pdf");
        assertThat(file.storedName()).isEqualTo("uuid.pdf");
        assertThat(file.size()).isEqualTo(1048576L);
        assertThat(file.sha256()).isEqualTo("f".repeat(64));
        assertThat(file.createdAt()).isEqualTo(1763432000123L);
    }

    @Test
    void missingFileShouldBeEmpty() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void storedNameShouldBeUnique() {
        repository.insert(uploaderId, "a.txt", "same-uuid", 1L, "hash-a", 1L);

        Throwable throwable = catchThrowable(() ->
                repository.insert(uploaderId, "b.txt", "same-uuid", 2L, "hash-b", 2L));

        assertThat(throwable).isInstanceOf(DataAccessException.class);
        assertThat(countRows("files")).isEqualTo(1L);
    }

    @Test
    void sizeShouldSupportLargeValues() {
        long largeSize = 20L * 1024 * 1024;
        long id = repository.insert(uploaderId, "big.bin", "uuid-big", largeSize, "a".repeat(64), 1L);

        assertThat(repository.findById(id).orElseThrow().size()).isEqualTo(largeSize);
    }
}
