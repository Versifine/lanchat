package com.lanchat.application.dto;

import java.io.InputStream;

/**
 * 文件下载载荷：元数据 + 文件输入流。
 *
 * <p>输入流由 Web 适配层负责在响应完成后关闭（Spring 的 InputStreamResource 会关闭它）。</p>
 */
public record FileDownload(FileView file, InputStream content) {
}
