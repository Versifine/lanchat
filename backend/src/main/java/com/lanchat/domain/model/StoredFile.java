package com.lanchat.domain.model;

/**
 * 文件落盘结果：磁盘安全文件名、实际字节数、SHA-256（小写十六进制）。
 */
public record StoredFile(String storedName, long size, String sha256) {
}
