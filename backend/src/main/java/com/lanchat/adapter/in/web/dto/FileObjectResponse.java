package com.lanchat.adapter.in.web.dto;

import com.lanchat.application.dto.FileView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件对象，对应契约中的 file 对象：
 * {@code {id, originalName, size, sha256, downloadUrl}}。
 */
@Schema(description = "文件元数据")
public record FileObjectResponse(
        @Schema(description = "文件 id") long id,
        @Schema(description = "清洗后的原始文件名") String originalName,
        @Schema(description = "文件字节数") long size,
        @Schema(description = "SHA-256（小写十六进制）") String sha256,
        @Schema(description = "下载地址") String downloadUrl) {

    public static FileObjectResponse from(FileView view) {
        return new FileObjectResponse(view.id(), view.originalName(), view.size(), view.sha256(), view.downloadUrl());
    }
}
