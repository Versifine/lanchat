package com.lanchat.domain.port;

import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储端口。实现位于 {@code adapter/out/persistence}（JdbcTemplate + PreparedStatement 参数化）。
 */
public interface UserRepository {

    /** 按用户名查账号（含密码哈希），登录使用。 */
    Optional<UserAccount> findByUsername(String username);

    /** 按 id 查账号（含密码哈希）。 */
    Optional<UserAccount> findById(long id);

    /** 按 id 查用户公开信息（不含密码材料）。 */
    Optional<User> findPublicById(long id);

    /** 全部用户公开信息，按 id 升序。 */
    List<User> findAll();

    /** 用户名是否已存在。 */
    boolean existsByUsername(String username);

    /**
     * 插入新用户，返回自增主键。
     *
     * @throws com.lanchat.domain.exception.BusinessException 用户名唯一约束冲突时返回错误码 1002
     */
    long insert(String username, String nickname, String passwordHash, long createdAt);
}
