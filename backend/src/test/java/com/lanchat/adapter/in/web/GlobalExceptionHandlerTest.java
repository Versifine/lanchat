package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.ApiErrorResponse;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理器单元测试：错误码 ↔ HTTP 状态码映射 + 统一错误体（docs/api.md 错误码表）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionShouldMapCodeToHttpStatus() {
        assertStatus(ErrorCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        assertStatus(ErrorCodes.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        assertStatus(ErrorCodes.USERNAME_TAKEN, HttpStatus.CONFLICT);
        assertStatus(ErrorCodes.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCodes.MESSAGE_TOO_LONG, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCodes.FILE_CHECKSUM_FAILED, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCodes.PROTOCOL_ERROR, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertStatus(ErrorCodes.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertStatus(ErrorCodes.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
        assertStatus(ErrorCodes.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
        assertStatus(ErrorCodes.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(9999, HttpStatus.INTERNAL_SERVER_ERROR); // 未知错误码兜底 500
    }

    private void assertStatus(int code, HttpStatus expectedStatus) {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(new BusinessException(code, "错误 " + code));

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isEqualTo("错误 " + code);
    }

    @Test
    void bindExceptionShouldReturn400WithFirstFieldMessage() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "username",
                "用户名需为 3-20 位字母、数字或下划线"));
        BindException exception = new BindException(bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleBindException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
        assertThat(response.getBody().message()).contains("3-20");
    }

    @Test
    void constraintViolationShouldReturn400WithViolationMessage() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("limit 必须在 1-200 之间");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
        assertThat(response.getBody().message()).isEqualTo("limit 必须在 1-200 之间");
    }

    @Test
    void malformedJsonShouldReturn400() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiErrorResponse> response = handler.handleMalformedRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
    }

    @Test
    void missingRequestParameterShouldReturn400() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("file", "MultipartFile");

        ResponseEntity<ApiErrorResponse> response = handler.handleMalformedRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
    }

    @Test
    void maxUploadSizeExceededShouldReturn413And1006() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(20L * 1024 * 1024);

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSize(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1006);
        assertThat(response.getBody().message()).contains("20MB");
    }

    @Test
    void multipartSizeLimitRootCauseShouldReturn413() {
        MultipartException exception = new MultipartException(
                "Failed to parse multipart servlet request", new SizeLimitExceededException());

        ResponseEntity<ApiErrorResponse> response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1006);
    }

    @Test
    void otherMultipartExceptionShouldReturn400() {
        MultipartException exception = new MultipartException("multipart 格式错误");

        ResponseEntity<ApiErrorResponse> response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
    }

    @Test
    void noResourceFoundShouldReturn404() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/api/nope");

        ResponseEntity<ApiErrorResponse> response = handler.handleNoResourceFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1004);
    }

    @Test
    void unexpectedExceptionShouldReturn500And1012() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpectedException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(1012);
        assertThat(response.getBody().message()).isEqualTo("服务器内部错误");
    }

    /** 模拟 Tomcat/Servlet 上传超限的根因异常类名（isSizeLimitExceeded 按类名关键字判断）。 */
    private static final class SizeLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
