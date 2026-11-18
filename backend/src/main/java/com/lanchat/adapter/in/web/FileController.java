package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.FileObjectResponse;
import com.lanchat.adapter.in.web.dto.UploadFileResponse;
import com.lanchat.application.dto.FileDownload;
import com.lanchat.application.dto.FileView;
import com.lanchat.application.service.FileService;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件接口（docs/api.md 第 5 节）。
 */
@Tag(name = "文件", description = "文件上传、元数据、下载")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传文件", description = "multipart/form-data：file 必填且 ≤20MB，to 选填（接收方用户 id，填写后通过 WebSocket 通知对方）",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadFileResponse upload(
            @Parameter(description = "文件内容，≤20MB") @RequestPart("file") MultipartFile file,
            @Parameter(description = "接收方用户 id（可选）") @RequestParam(name = "to", required = false) Long to,
            @Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        try (InputStream content = file.getInputStream()) {
            FileView view = fileService.upload(
                    authUser.id(),
                    to,
                    file.getOriginalFilename(),
                    file.getSize(),
                    content);
            return UploadFileResponse.from(view);
        } catch (IOException e) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "读取上传文件失败");
        }
    }

    @Operation(summary = "文件元数据", description = "文件不存在返回 404/1007",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public FileObjectResponse metadata(@Parameter(description = "文件 id") @PathVariable("id") long id) {
        return FileObjectResponse.from(fileService.metadata(id));
    }

    @Operation(summary = "下载文件", description = "返回文件流，Content-Disposition: attachment；也支持 ?token=<JWT> 查询参数（浏览器直接下载场景）",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@Parameter(description = "文件 id") @PathVariable("id") long id) {
        FileDownload download = fileService.download(id);
        String contentDisposition = ContentDisposition.attachment()
                .filename(download.file().originalName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.file().size())
                .body(new InputStreamResource(download.content()));
    }
}
