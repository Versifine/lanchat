package com.lanchat.application.dto;

import com.lanchat.domain.model.ChatFile;

/**
 * 文件视图，对应契约中的 file 对象：
 * {@code {id, originalName, size, sha256, downloadUrl}}。
 */
public record FileView(long id, String originalName, long size, String sha256, String downloadUrl) {

    public static FileView from(ChatFile file) {
        return new FileView(
                file.id(),
                file.originalName(),
                file.size(),
                file.sha256(),
                "/api/files/" + file.id() + "/download");
    }
}
