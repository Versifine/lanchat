package com.lanchat.infrastructure.rate;

import com.lanchat.infrastructure.config.LanChatProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息发送限流单元测试（SEC-08 / EV-SEC-11）：单用户 20 条/秒。
 */
class InMemoryMessageRateLimiterTest {

    private static LanChatProperties properties(int messagesPerSecond) {
        return new LanChatProperties(
                new LanChatProperties.Jwt("unit-test-secret-key-0123456789-abcdefghij", 24),
                new LanChatProperties.Upload("data/uploads", 20L * 1024 * 1024),
                new LanChatProperties.History(50),
                new LanChatProperties.Heartbeat(35_000),
                new LanChatProperties.RateLimit(5, messagesPerSecond));
    }

    @Test
    void shouldAllowConfiguredBurstThenReject() {
        InMemoryMessageRateLimiter limiter = new InMemoryMessageRateLimiter(properties(20));

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire(1L)).as("第 %d 条应被允许", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire(1L)).as("第 21 条应被限流").isFalse();
    }

    @Test
    void differentUsersShouldHaveIndependentWindows() {
        InMemoryMessageRateLimiter limiter = new InMemoryMessageRateLimiter(properties(1));

        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    void windowShouldRefillAfterOneSecond() throws InterruptedException {
        InMemoryMessageRateLimiter limiter = new InMemoryMessageRateLimiter(properties(1));

        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();

        Thread.sleep(1_100L);

        assertThat(limiter.tryAcquire(1L)).as("窗口滚动后应恢复").isTrue();
    }
}
