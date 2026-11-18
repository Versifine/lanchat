package com.lanchat.domain.exception;

/**
 * 业务错误码常量，与 {@code docs/api.md}「错误码」表逐字对应。
 *
 * <p>领域层只关心业务错误码；HTTP 状态码由 Web 适配层统一映射，
 * WebSocket 适配层则把错误码原样放进 error 帧。</p>
 */
public final class ErrorCodes {

    /** 1001 未认证/未登录（HTTP 401）。 */
    public static final int UNAUTHORIZED = 1001;
    /** 1002 用户名已存在（HTTP 409）。 */
    public static final int USERNAME_TAKEN = 1002;
    /** 1003 用户名或密码错误（HTTP 401）。 */
    public static final int BAD_CREDENTIALS = 1003;
    /** 1004 参数非法（HTTP 400）。 */
    public static final int INVALID_ARGUMENT = 1004;
    /** 1005 用户不存在（HTTP 404）。 */
    public static final int USER_NOT_FOUND = 1005;
    /** 1006 文件过大（HTTP 413）。 */
    public static final int FILE_TOO_LARGE = 1006;
    /** 1007 文件不存在/无权访问（HTTP 404）。 */
    public static final int FILE_NOT_FOUND = 1007;
    /** 1008 消息过长（HTTP 400 / WS error 帧）。 */
    public static final int MESSAGE_TOO_LONG = 1008;
    /** 1009 请求过于频繁（HTTP 429 / WS error 帧）。 */
    public static final int RATE_LIMITED = 1009;
    /** 1010 文件校验失败（HTTP 400）。 */
    public static final int FILE_CHECKSUM_FAILED = 1010;
    /** 1011 协议错误（HTTP 400 / WS error 帧）。 */
    public static final int PROTOCOL_ERROR = 1011;
    /** 1012 服务器内部错误（HTTP 500）。 */
    public static final int INTERNAL_ERROR = 1012;

    private ErrorCodes() {
    }
}
