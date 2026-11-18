package com.lanchat.domain.exception;

/**
 * 业务异常：携带契约错误码 + 面向用户的中文提示。
 *
 * <p>领域层/应用层抛出此异常；由 Web 适配层的 {@code GlobalExceptionHandler} 转换为
 * {@code {"code":..,"message":..}}，由 WebSocket 适配层转换为
 * {@code {"type":"error","code":..,"message":..}} 帧。领域层不依赖 Spring / Servlet。</p>
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
