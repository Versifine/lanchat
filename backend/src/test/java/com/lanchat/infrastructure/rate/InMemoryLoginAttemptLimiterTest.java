package com.lanchat.infrastructure.rate;

import com.lanchat.infrastructure.config.LanChatProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录失败限流单元测试（SEC-08 / EV-SEC-10）：5 次/分钟失败后锁定 1 分钟。
 */
class InMemoryLoginAttemptLimiterTest {

    private static LanChatProperties properties(int loginFailPerMinute) {
        return new LanChatProperties(
                new LanChatProperties.Jwt("unit-test-secret-key-0123456789-abcdefghij", 24),
                new LanChatProperties.Upload("data/uploads", 20L * 1024 * 1024),
                new LanChatProperties.History(50),
                new LanChatProperties.Heartbeat(35_000),
                new LanChatProperties.RateLimit(loginFailPerMinute, 20));
    }

    @Test
    void lockShouldTriggerAfterConfiguredFailureCount() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(properties(5));

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("alice");
            assertThat(limiter.isLocked("alice")).as("第 %d 次失败后不应锁定", i + 1).isFalse();
        }
        limiter.recordFailure("alice"); // 第 5 次
        assertThat(limiter.isLocked("alice")).isTrue();
    }

    @Test
    void differentUsernamesShouldHaveIndependentWindows() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(properties(2));

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");
        assertThat(limiter.isLocked("alice")).isTrue();

        assertThat(limiter.isLocked("bob")).isFalse();
        limiter.recordFailure("bob");
        assertThat(limiter.isLocked("bob")).isFalse();
    }

    @Test
    void successfulLoginShouldClearFailureWindow() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(properties(2));

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");
        assertThat(limiter.isLocked("alice")).isTrue();

        limiter.recordSuccess("alice");

        assertThat(limiter.isLocked("alice")).isFalse();
    }

    @Test
    void nullUsernamesShouldBeIgnored() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(properties(1));

        limiter.recordFailure(null);
        limiter.recordSuccess(null);

        assertThat(limiter.isLocked(null)).isFalse();
    }
}
