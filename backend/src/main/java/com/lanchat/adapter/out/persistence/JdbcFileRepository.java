package com.lanchat.adapter.out.persistence;

import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.port.FileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件元数据仓储的 SQLite 实现。
 */
@Repository
public class JdbcFileRepository implements FileRepository {

    private static final RowMapper<ChatFile> FILE_ROW_MAPPER = (resultSet, rowNum) -> new ChatFile(
            resultSet.getLong("id"),
            resultSet.getLong("uploader_id"),
            resultSet.getString("original_name"),
            resultSet.getString("stored_name"),
            resultSet.getLong("size"),
            resultSet.getString("sha256"),
            resultSet.getLong("created_at"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long insert(long uploaderId,
                       String originalName,
                       String storedName,
                       long size,
                       String sha256,
                       long createdAt) {
        return JdbcInsertHelper.insert(jdbcTemplate,
                "INSERT INTO files (uploader_id, original_name, stored_name, size, sha256, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                uploaderId,
                originalName,
                storedName,
                size,
                sha256,
                createdAt);
    }

    @Override
    public Optional<ChatFile> findById(long id) {
        List<ChatFile> files = jdbcTemplate.query(
                """
                SELECT id, uploader_id, original_name, stored_name, size, sha256, created_at
                FROM files
                WHERE id = ?
                """,
                FILE_ROW_MAPPER,
                id);
        return files.stream().findFirst();
    }
}
