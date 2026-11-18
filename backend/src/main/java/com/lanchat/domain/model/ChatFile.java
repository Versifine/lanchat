package com.lanchat.domain.model;

/**
 * 文件元数据。
 *
 * <p>storedName 是清洗后生成的磁盘安全文件名（UUID + 安全扩展名），只用于服务端落盘定位，
 * 不对外暴露；客户端看到的是 originalName（已清洗的可展示文件名）。</p>
 */
public record ChatFile(long id,
                       long uploaderId,
                       String originalName,
                       String storedName,
                       long size,
                       String sha256,
                       long createdAt) {
}
