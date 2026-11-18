package com.lanchat.domain.port;

import com.lanchat.domain.model.AuthUser;

import java.util.Optional;

/**
 * JWT 签发/校验端口。实现位于 {@code infrastructure/security}（jjwt, HS256）。
 */
public interface TokenIssuer {

    /** 为用户签发 JWT（HS256，包含 userId 与 username，带过期时间）。 */
    String issue(long userId, String username);

    /**
     * 校验签名与有效期。
     *
     * @return token 缺失/非法/过期时返回 {@link Optional#empty()}
     */
    Optional<AuthUser> verify(String token);
}
