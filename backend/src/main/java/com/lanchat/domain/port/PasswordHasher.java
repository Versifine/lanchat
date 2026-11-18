package com.lanchat.domain.port;

/**
 * 密码哈希端口。实现位于 {@code infrastructure/security}（PBKDF2WithHmacSHA256）。
 */
public interface PasswordHasher {

    /**
     * 生成密码哈希，存储格式固定为 {@code pbkdf2$迭代次数$盐hex$哈希hex}。
     */
    String hash(String rawPassword);

    /**
     * 校验明文密码与已存储哈希是否匹配（使用恒定时间比较，防时序攻击）。
     *
     * @return 哈希格式非法或密码不匹配时返回 false
     */
    boolean matches(String rawPassword, String encodedPassword);
}
