package com.lanchat.adapter.in.web;

/**
 * 认证上下文在 {@code HttpServletRequest} 中的属性键。
 *
 * <p>{@link JwtAuthenticationFilter} 校验 JWT 成功后写入，
 * {@link CurrentUserArgumentResolver} 读取后注入 Controller 参数。</p>
 */
public final class AuthAttributes {

    /** 当前登录用户（{@link com.lanchat.domain.model.AuthUser}）。 */
    public static final String AUTH_USER = "lanchat.auth.user";

    private AuthAttributes() {
    }
}
