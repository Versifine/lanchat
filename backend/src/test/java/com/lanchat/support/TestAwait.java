package com.lanchat.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 异步断言助手：轮询等待某个条件成立，超时抛出带描述的 {@link AssertionError}。
 *
 * <p>项目没有引入 Awaitility 依赖，这里用最简单的轮询实现，避免额外依赖。</p>
 */
public final class TestAwait {

    private static final long POLL_MILLIS = 20L;

    private TestAwait() {
    }

    /** 在 timeout 内轮询等待 condition 返回 true，否则抛出 AssertionError。 */
    public static void until(Duration timeout, String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待被中断: " + description, e);
            }
        }
        if (condition.getAsBoolean()) {
            return;
        }
        throw new AssertionError("等待超时（" + timeout.toMillis() + "ms）: " + description);
    }
}
