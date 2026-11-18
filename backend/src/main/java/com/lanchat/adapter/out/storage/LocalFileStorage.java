package com.lanchat.adapter.out.storage;

import com.lanchat.application.service.FileNameCleaner;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.StoredFile;
import com.lanchat.domain.port.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 本地文件存储实现。
 *
 * <p>安全设计：</p>
 * <ul>
 *   <li>上传目录固定（来自配置），落盘文件名 = UUID + 安全扩展名，不含任何用户输入路径；</li>
 *   <li>写入时再次校验路径必须位于上传目录内（normalize + startsWith），双保险防目录穿越；</li>
 *   <li>先写临时文件、边写边算 SHA-256、边统计大小，超过上限立即中止并删除临时文件；</li>
 *   <li>全部使用 try-with-resources，避免文件句柄泄漏。</li>
 * </ul>
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final int BUFFER_SIZE = 8192;

    private final Path baseDirectory;

    public LocalFileStorage(String uploadDirectory) {
        this.baseDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + baseDirectory, e);
        }
        log.info("文件上传目录: {}", baseDirectory);
    }

    @Override
    public StoredFile store(InputStream input, String originalName, long maxBytes) {
        if (input == null) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "上传文件内容为空");
        }
        String storedName = generateStoredName(originalName);
        Path target = resolveSafely(storedName);
        Path temporaryFile = null;
        MessageDigest digest = newDigest();
        long totalBytes = 0;
        try {
            temporaryFile = Files.createTempFile(baseDirectory, ".upload-", ".tmp");
            try (OutputStream outputStream = new DigestOutputStream(Files.newOutputStream(temporaryFile), digest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > maxBytes) {
                        throw new BusinessException(ErrorCodes.FILE_TOO_LARGE, "文件过大，最大允许 "
                                + Math.max(1L, maxBytes / (1024L * 1024L)) + " MB");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            temporaryFile = null;
            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(storedName, totalBytes, sha256);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("文件落盘失败: storedName={}", storedName, e);
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "文件保存失败，请稍后重试");
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public InputStream open(String storedName) {
        Path path = resolveSafely(storedName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCodes.FILE_NOT_FOUND, "文件不存在或无权访问");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            log.error("打开文件失败: storedName={}", storedName, e);
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "文件读取失败");
        }
    }

    @Override
    public void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolveSafely(storedName));
        } catch (RuntimeException | IOException e) {
            log.warn("清理文件失败: storedName={}, reason={}", storedName, e.getMessage());
        }
    }

    /**
     * 生成磁盘安全文件名：UUID（去横线）+ 安全扩展名。
     */
    private static String generateStoredName(String originalName) {
        String extension = FileNameCleaner.safeExtension(originalName);
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /**
     * 把文件名解析为上传目录内的绝对路径，并校验没有逃逸出上传目录。
     */
    private Path resolveSafely(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new BusinessException(ErrorCodes.FILE_NOT_FOUND, "文件不存在或无权访问");
        }
        Path resolved = baseDirectory.resolve(storedName).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new BusinessException(ErrorCodes.FILE_NOT_FOUND, "文件不存在或无权访问");
        }
        return resolved;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", temporaryFile, e);
        }
    }
}
