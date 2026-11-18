package com.lanchat.domain.port;

import com.lanchat.domain.model.StoredFile;

import java.io.InputStream;

/**
 * 文件存储端口。实现位于 {@code adapter/out/storage}（本地固定目录）。
 */
public interface FileStorage {

    /**
     * 流式落盘并计算 SHA-256（不把整个文件读进内存）。
     *
     * @param originalName 已清洗的原始文件名（只用于推导安全扩展名）
     * @param maxBytes     允许的最大字节数
     * @throws com.lanchat.domain.exception.BusinessException 超过 maxBytes 时错误码 1006；IO 失败时 1012
     */
    StoredFile store(InputStream input, String originalName, long maxBytes);

    /**
     * 打开已存储文件的输入流，调用方负责关闭。
     *
     * @throws com.lanchat.domain.exception.BusinessException 文件不存在时错误码 1007
     */
    InputStream open(String storedName);

    /** 删除文件（忽略异常），用于入库失败时清理孤儿文件。 */
    void deleteQuietly(String storedName);
}
