package com.lanchat.domain.port;

import com.lanchat.domain.model.ChatFile;

import java.util.Optional;

/**
 * 文件元数据仓储端口。实现位于 {@code adapter/out/persistence}。
 */
public interface FileRepository {

    /** 插入文件元数据，返回自增主键。 */
    long insert(long uploaderId,
                String originalName,
                String storedName,
                long size,
                String sha256,
                long createdAt);

    /** 按 id 查文件元数据。 */
    Optional<ChatFile> findById(long id);
}
