package com.lanchat.infrastructure.security;

import com.lanchat.domain.model.AuthUser;
import com.lanchat.infrastructure.config.LanChatProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发/校验单元测试（SEC-02 / EV-SEC-02）。
 */
class JwtTokenIssuerTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";
    private static final String OTHER_SECRET = "other-unit-test-secret-key-0123456789-abcd";

    private static LanChatProperties properties(String secret, long ttlHours) {
        return new LanChatProperties(
                new LanChatProperties.Jwt(secret, ttlHours),
                new LanChatProperties.Upload("data/uploads", 20L * 1024 * 1024),
                new LanChatProperties.History(50),
                new LanChatProperties.Heartbeat(35_000),
                new LanChatProperties.RateLimit(5, 20));
    }

    @Test
    void issuedTokenShouldVerifyBackToSameUser() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));

        String token = issuer.issue(7L, "alice");

        assertThat(token).isNotBlank();
        assertThat(issuer.verify(token)).hasValue(new AuthUser(7L, "alice"));
    }

    @Test
    void tokenHeaderShouldUseHs256() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));

        String token = issuer.issue(1L, "alice");
        String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                StandardCharsets.UTF_8);

        assertThat(headerJson).contains("\"alg\":\"HS256\"");
    }

    @Test
    void tamperedPayloadShouldBeRejected() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));
        String token = issuer.issue(1L, "alice");
        String[] parts = token.split("\\.");

        char first = parts[1].charAt(0);
        String tamperedPayload = (first == 'a' ? "b" : "a") + parts[1].substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(issuer.verify(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretShouldBeRejected() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));
        JwtTokenIssuer otherIssuer = new JwtTokenIssuer(properties(OTHER_SECRET, 24));

        String token = issuer.issue(1L, "alice");

        assertThat(otherIssuer.verify(token)).isEmpty();
    }

    @Test
    void expiredTokenShouldBeRejected() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, -1));

        String expired = issuer.issue(1L, "alice");

        assertThat(issuer.verify(expired)).isEmpty();
    }

    @Test
    void invalidTokenStringsShouldBeRejected() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));

        assertThat(issuer.verify(null)).isEmpty();
        assertThat(issuer.verify("")).isEmpty();
        assertThat(issuer.verify("   ")).isEmpty();
        assertThat(issuer.verify("not-a-jwt")).isEmpty();
        assertThat(issuer.verify("a.b.c")).isEmpty();
    }

    @Test
    void weakSecretShouldFailFast() {
        assertThatThrownBy(() -> new JwtTokenIssuer(properties("too-short-secret", 24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void issueShouldProduceThreePartCompactToken() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties(SECRET, 24));

        String token = issuer.issue(1L, "alice");

        assertThat(token.split("\\.")).hasSize(3);
        // 载荷是 base64url，不应出现明文用户名
        assertThat(token).doesNotContain("alice");
    }
}
