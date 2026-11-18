package com.lanchat.infrastructure.security;

import com.lanchat.domain.port.PasswordHasher;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * PBKDF2WithHmacSHA256 密码哈希实现（SEC-01）。
 *
 * <p>参数与存储格式：</p>
 * <ul>
 *   <li>算法：PBKDF2WithHmacSHA256，迭代 120000 次，16 字节随机盐，256 位输出；</li>
 *   <li>存储格式：{@code pbkdf2$迭代次数$盐hex$哈希hex}（迭代次数/盐随哈希保存，便于将来升级参数）；</li>
 *   <li>校验用 {@link MessageDigest#isEqual} 恒定时间比较；</li>
 *   <li>禁止明文/MD5/SHA1。</li>
 * </ul>
 */
@Component
public class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_PREFIX = "pbkdf2";
    private static final String SEPARATOR = "\\$";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final Pattern ITERATIONS_PATTERN = Pattern.compile("\\d{1,9}");

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS / 8);
        HexFormat hex = HexFormat.of();
        return FORMAT_PREFIX + "$" + ITERATIONS + "$" + hex.formatHex(salt) + "$" + hex.formatHex(hash);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            String[] parts = encodedPassword.split(SEPARATOR);
            if (parts.length != 4 || !FORMAT_PREFIX.equals(parts[0]) || !ITERATIONS_PATTERN.matcher(parts[1]).matches()) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1) {
                return false;
            }
            HexFormat hex = HexFormat.of();
            byte[] salt = hex.parseHex(parts[2]);
            byte[] expected = hex.parseHex(parts[3]);
            if (expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            // 哈希格式非法（被篡改/旧格式）时视为不匹配，不抛出内部异常泄露细节
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBytes) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLengthBytes * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 计算失败", e);
        } finally {
            keySpec.clearPassword();
        }
    }
}
