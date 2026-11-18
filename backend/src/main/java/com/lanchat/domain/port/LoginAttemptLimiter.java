package com.lanchat.domain.port;

/**
 * 登录失败限流端口（SEC-08：同一用户名 1 分钟内失败 5 次后锁定 1 分钟）。
 *
 * <p>实现位于 {@code infrastructure/rate}，采用内存滑动窗口。</p>
 */
public interface LoginAttemptLimiter {

    /** 该用户名当前是否处于锁定状态。 */
    boolean isLocked(String username);

    /** 记录一次登录失败（密码错误）。 */
    void recordFailure(String username);

    /** 登录成功，清除该用户名的失败计数。 */
    void recordSuccess(String username);
}
