package com.lanchat.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PBKDF2 密码哈希单元测试（EV-SEC-01 / SEC-01）。
 *
 * <p>覆盖：契约存储格式 {@code pbkdf2$迭代次数$盐hex$哈希hex}、随机盐、正确/错误密码校验、
 * 非法编码容错（不抛内部异常）、空密码拒绝。</p>
 */
class Pbkdf2PasswordHasherTest {

    /** 契约格式：pbkdf2$120000$32位十六进制盐$64位十六进制哈希（256 位）。 */
    private static final Pattern STORAGE_FORMAT =
            Pattern.compile("^pbkdf2\\$120000\\$[0-9a-f]{32}\\$[0-9a-f]{64}$");

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashShouldFollowContractFormat() {
        String encoded = hasher.hash("abc123");

        assertThat(encoded).matches(STORAGE_FORMAT);
        assertThat(encoded).doesNotContain("abc123");
        String[] parts = encoded.split("\\$");
        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("pbkdf2");
        assertThat(parts[1]).isEqualTo("120000");
        assertThat(parts[2]).hasSize(32);   // 16 字节盐
        assertThat(parts[3]).hasSize(64);   // 32 字节摘要
    }

    @Test
    void hashShouldUseRandomSaltSoSamePasswordProducesDifferentHashes() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(first.split("\\$")[2]).isNotEqualTo(second.split("\\$")[2]);
        assertThat(hasher.matches("same-password", first)).isTrue();
        assertThat(hasher.matches("same-password", second)).isTrue();
    }

    @Test
    void matchesShouldAcceptCorrectPassword() {
        String encoded = hasher.hash("P@ssw0rd-中文密码");

        assertThat(hasher.matches("P@ssw0rd-中文密码", encoded)).isTrue();
    }

    @Test
    void matchesShouldRejectWrongPassword() {
        String encoded = hasher.hash("correct-password");

        assertThat(hasher.matches("wrong-password", encoded)).isFalse();
        assertThat(hasher.matches("Correct-password", encoded)).isFalse();
        assertThat(hasher.matches("correct-password ", encoded)).isFalse();
        assertThat(hasher.matches("", encoded)).isFalse();
    }

    @Test
    void matchesShouldRejectMalformedOrTamperedEncodingWithoutThrowing() {
        String valid = hasher.hash("abc123");
        String[] parts = valid.split("\\$");

        // 篡改哈希末位（长度不变、不是原密码）
        String lastChar = parts[3].substring(63);
        String flipped = "0".equals(lastChar) ? "1" : "0";
        String tamperedHash = parts[0] + "$" + parts[1] + "$" + parts[2] + "$" + parts[3].substring(0, 63) + flipped;

        assertThat(hasher.matches("abc123", tamperedHash)).isFalse();

        String[] malformed = {
                "",                                  // 空串
                "plaintext",                         // 明文/旧格式
                "md5$120000$00$00",                  // 错误算法前缀
                "pbkdf2$120000$" + parts[2],         // 缺哈希段
                "pbkdf2$abc$" + parts[2] + "$" + parts[3],       // 迭代次数非数字
                "pbkdf2$120000$zz$" + parts[3],                  // 盐不是十六进制
                "pbkdf2$120000$" + parts[2] + "$",               // 哈希为空
                "pbkdf2$120000$" + parts[2] + "$" + parts[3] + "$extra", // 段数过多
        };
        for (String encoded : malformed) {
            assertThat(hasher.matches("abc123", encoded))
                    .as("非法哈希编码应视为不匹配: %s", encoded)
                    .isFalse();
        }
        assertThat(hasher.matches(null, valid)).isFalse();
        assertThat(hasher.matches("abc123", null)).isFalse();
    }

    @Test
    void hashShouldRejectNullOrEmptyPassword() {
        assertThatThrownBy(() -> hasher.hash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码不能为空");
        assertThatThrownBy(() -> hasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码不能为空");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "6-32", "中文密码可以吗", "!@#$%^&*()_+-=[]{}"})
    void hashShouldSupportVariousCharacterSets(String password) {
        String encoded = hasher.hash(password);

        assertThat(encoded).matches(STORAGE_FORMAT);
        assertThat(hasher.matches(password, encoded)).isTrue();
    }
}
