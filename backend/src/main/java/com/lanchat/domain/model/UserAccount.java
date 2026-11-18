package com.lanchat.domain.model;

/**
 * 用户账号聚合（含密码哈希），只在认证用例与仓储内部流转，<b>绝不允许</b>序列化给客户端。
 *
 * <p>passwordHash 的存储格式为 {@code pbkdf2$迭代次数$盐hex$哈希hex}，
 * 盐与迭代次数随哈希一起保存，便于以后调整迭代次数而不影响老账号校验。</p>
 */
public record UserAccount(long id, String username, String nickname, String passwordHash, long createdAt) {
}
