package com.lanchat.application.service;

import com.lanchat.application.config.AppSettings;
import com.lanchat.application.dto.FileDownload;
import com.lanchat.application.dto.FileView;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.StoredFile;
import com.lanchat.domain.model.User;
import com.lanchat.domain.port.FileRepository;
import com.lanchat.domain.port.FileStorage;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * 文件用例服务：上传（流式落盘 + SHA-256 + 入库 + WS 通知接收方）、元数据查询、下载。
 *
 * <p>安全要点（SEC-05）：文件名先清洗再使用；真实落盘名是 UUID + 安全扩展名；
 * 大小受限（配置 20MB，超出错误码 1006）；所有文件写入配置的固定上传目录。</p>
 */
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final RealtimeNotifier realtimeNotifier;
    private final AppSettings appSettings;

    public FileService(FileRepository fileRepository,
                       UserRepository userRepository,
                       FileStorage fileStorage,
                       RealtimeNotifier realtimeNotifier,
                       AppSettings appSettings) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.realtimeNotifier = realtimeNotifier;
        this.appSettings = appSettings;
    }

    /**
     * 上传文件。
     *
     * @param uploaderId      上传者用户 id（来自 JWT）
     * @param receiverId      可选接收方；非空时通过 WS 通知对方
     * @param originalFileName 客户端原始文件名（会被清洗）
     * @param declaredSize    客户端声明大小（用于提前拒绝超大文件）
     * @param content         文件内容流（由 Web 适配层负责关闭）
     */
    public FileView upload(long uploaderId,
                           Long receiverId,
                           String originalFileName,
                           long declaredSize,
                           InputStream content) {
        if (declaredSize > appSettings.uploadMaxBytes()) {
            throw new BusinessException(ErrorCodes.FILE_TOO_LARGE, fileTooLargeMessage());
        }
        String originalName = FileNameCleaner.sanitize(originalFileName);
        FileNameCleaner.requireValid(originalName);
        User uploader = userRepository.findPublicById(uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        User receiver = null;
        if (receiverId != null) {
            receiver = userRepository.findPublicById(receiverId)
                    .orElseThrow(() -> new BusinessException(ErrorCodes.USER_NOT_FOUND, "接收方用户不存在"));
        }
        StoredFile stored = fileStorage.store(content, originalName, appSettings.uploadMaxBytes());
        long now = System.currentTimeMillis();
        long fileId;
        try {
            fileId = fileRepository.insert(uploaderId, originalName, stored.storedName(), stored.size(), stored.sha256(), now);
        } catch (RuntimeException e) {
            // 入库失败时清理已落盘文件，避免产生孤儿文件
            fileStorage.deleteQuietly(stored.storedName());
            throw e;
        }
        ChatFile file = new ChatFile(fileId, uploaderId, originalName, stored.storedName(), stored.size(), stored.sha256(), now);
        log.info("文件上传成功: id={}, uploaderId={}, name={}, size={}, sha256={}",
                fileId, uploaderId, originalName, stored.size(), stored.sha256());
        if (receiver != null) {
            realtimeNotifier.sendFile(receiver.id(), file, uploader);
        }
        return FileView.from(file);
    }

    /** 查询文件元数据（GET /api/files/{id}）。 */
    public FileView metadata(long fileId) {
        return FileView.from(requireFile(fileId));
    }

    /** 下载文件（GET /api/files/{id}/download），调用方负责关闭输入流。 */
    public FileDownload download(long fileId) {
        ChatFile file = requireFile(fileId);
        InputStream content = fileStorage.open(file.storedName());
        return new FileDownload(FileView.from(file), content);
    }

    private ChatFile requireFile(long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.FILE_NOT_FOUND, "文件不存在或无权访问"));
    }

    private String fileTooLargeMessage() {
        long megabytes = Math.max(1L, appSettings.uploadMaxBytes() / (1024L * 1024L));
        return "文件过大，最大允许 " + megabytes + " MB";
    }
}
