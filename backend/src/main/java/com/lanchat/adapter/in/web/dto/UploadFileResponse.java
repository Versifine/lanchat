package com.lanchat.adapter.in.web.dto;

import com.lanchat.application.dto.FileView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 上传成功响应：{@code {"file":{id,originalName,size,sha256,downloadUrl}}}。
 */
@Schema(description = "文件上传成功响应")
public record UploadFileResponse(
        @Schema(description = "文件元数据") FileObjectResponse file) {

    public static UploadFileResponse from(FileView view) {
        return new UploadFileResponse(FileObjectResponse.from(view));
    }
}
