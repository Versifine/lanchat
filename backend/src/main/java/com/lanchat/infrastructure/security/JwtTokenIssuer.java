package com.lanchat.infrastructure.security;

import com.lanchat.domain.model.AuthUser;
import com.lanchat.domain.port.TokenIssuer;
import com.lanchat.infrastructure.config.LanChatProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发/校验实现（jjwt 0.12，HS256）。
 *
 * <p>安全要点（SEC-02）：</p>
 * <ul>
 *   <li>密钥从配置注入；启动时校验长度 ≥32 字节，不满足直接启动失败（避免误用弱密钥）；</li>
 *   <li>校验签名与过期时间，任何异常一律返回 empty（不向调用方区分原因）；</li>
 *   <li>日志只记录失败原因，<b>绝不打印完整 token</b>（SEC-09）。</li>
 * </ul>
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenIssuer.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String USERNAME_CLAIM = "username";

    private final SecretKey signingKey;
    private final long ttlMillis;

    public JwtTokenIssuer(LanChatProperties properties) {
        String secret = properties.jwt().secret();
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("lanchat.jwt.secret 长度必须不少于 32 字节（HS256 要求），当前为 "
                    + secretBytes.length + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.ttlMillis = properties.jwt().ttlHours() * 3_600_000L;
    }

    @Override
    public String issue(long userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim(USERNAME_CLAIM, username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public Optional<AuthUser> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long userId = Long.parseLong(claims.getSubject());
            String username = claims.get(USERNAME_CLAIM, String.class);
            return Optional.of(new AuthUser(userId, username == null ? "" : username));
        } catch (JwtException | IllegalArgumentException e) {
            // 只记录原因，不记录 token 内容
            log.debug("JWT 校验失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
