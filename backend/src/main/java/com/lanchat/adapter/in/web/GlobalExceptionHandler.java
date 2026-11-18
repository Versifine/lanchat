package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.ApiErrorResponse;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各类异常统一转换为契约错误体
 * {@code {"code":<int>,"message":"<中文说明>"}}，并映射对应 HTTP 状态码。
 *
 * <p>错误码 ↔ HTTP 状态码映射见 docs/api.md「错误码」表；</p>
 * <p>日志只记录服务端内部错误堆栈，不记录密码/token 等敏感信息（SEC-09）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：错误码与 HTTP 状态码并存。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(statusFor(e.code()))
                .body(new ApiErrorResponse(e.code(), e.getMessage()));
    }

    /** 请求体校验失败（@Valid）：400 / 1004。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数非法");
        return badRequest(message);
    }

    /** 方法参数校验失败（@RequestParam/@PathVariable 上的约束）：400 / 1004。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数非法");
        return badRequest(message);
    }

    /** 请求体不是合法 JSON、参数类型不匹配、缺少必需参数/文件部分等：400 / 1004。 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception e) {
        log.debug("请求参数非法: {}", e.getMessage());
        return badRequest("请求参数非法");
    }

    /** 上传文件超过大小限制（Spring multipart 限制）：413 / 1006。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.debug("上传文件超过限制: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(ErrorCodes.FILE_TOO_LARGE, "文件过大，最大允许 20MB"));
    }

    /** 其余 multipart 解析异常：根据根因区分“超大文件(1006)”与“参数非法(1004)”。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException e) {
        log.debug("multipart 解析失败: {}", e.getMessage());
        if (isSizeLimitExceeded(e)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ApiErrorResponse(ErrorCodes.FILE_TOO_LARGE, "文件过大，最大允许 20MB"));
        }
        return badRequest("请求参数非法");
    }

    /** 不存在的接口/静态资源：404。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(ErrorCodes.INVALID_ARGUMENT, "接口或资源不存在"));
    }

    /** 兜底：500 / 1012，记录堆栈便于排查。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception e) {
        log.error("未处理的服务器异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(ErrorCodes.INTERNAL_ERROR, "服务器内部错误"));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(ErrorCodes.INVALID_ARGUMENT, message));
    }

    /**
     * 业务错误码 → HTTP 状态码（docs/api.md 错误码表）。
     */
    private static HttpStatus statusFor(int code) {
        return switch (code) {
            case ErrorCodes.UNAUTHORIZED, ErrorCodes.BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ErrorCodes.USERNAME_TAKEN -> HttpStatus.CONFLICT;
            case ErrorCodes.INVALID_ARGUMENT, ErrorCodes.MESSAGE_TOO_LONG,
                 ErrorCodes.FILE_CHECKSUM_FAILED, ErrorCodes.PROTOCOL_ERROR -> HttpStatus.BAD_REQUEST;
            case ErrorCodes.USER_NOT_FOUND, ErrorCodes.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCodes.FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case ErrorCodes.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static boolean isSizeLimitExceeded(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String simpleName = current.getClass().getSimpleName();
            if (simpleName.contains("SizeLimitExceeded") || simpleName.contains("MaxUploadSizeExceeded")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
