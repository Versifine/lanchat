package com.lanchat.adapter.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Controller 参数为“当前登录用户”，由 {@link CurrentUserArgumentResolver} 注入。
 *
 * <p>发送者身份只从 JWT 解析（SEC-04），Controller 无需、也不允许自己解析请求体里的用户字段。</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
