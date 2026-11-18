package com.lanchat.domain.port;

/**
 * 消息发送限流端口（SEC-08：单用户每秒最多 20 条消息）。
 *
 * <p>实现位于 {@code infrastructure/rate}，采用按用户的内存滑动窗口。</p>
 */
public interface MessageRateLimiter {

    /**
     * 尝试为 userId 获取一次发送配额。
     *
     * @return 超过每秒上限时返回 false（调用方返回错误码 1009）
     */
    boolean tryAcquire(long userId);
}
