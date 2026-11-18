package com.lanchat.infrastructure.rate;

import com.lanchat.domain.port.MessageRateLimiter;
import com.lanchat.infrastructure.config.LanChatProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息发送限流的内存实现（SEC-08 / EV-SEC-11）。
 *
 * <p>按用户固定 1 秒窗口计数，默认上限 20 条/秒。窗口实现为
 * {@link ConcurrentHashMap} + 单个窗口内 {@code synchronized}，并发安全且开销极小。
 * 在线用户数有限，条目规模可控；超过阈值时顺带清理已过期的窗口。</p>
 */
@Component
public class InMemoryMessageRateLimiter implements MessageRateLimiter {

    private static final long WINDOW_MILLIS = 1_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxMessagesPerWindow;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    public InMemoryMessageRateLimiter(LanChatProperties properties) {
        this.maxMessagesPerWindow = Math.max(1, properties.rateLimit().messagesPerSecond());
    }

    @Override
    public boolean tryAcquire(long userId) {
        Window window = windows.computeIfAbsent(userId, key -> new Window());
        cleanupIfNeeded();
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStart >= WINDOW_MILLIS) {
                window.windowStart = now;
                window.count = 0;
            }
            if (window.count >= maxMessagesPerWindow) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void cleanupIfNeeded() {
        if (windows.size() < CLEANUP_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> {
            Window window = entry.getValue();
            synchronized (window) {
                return now - window.windowStart >= WINDOW_MILLIS && window.count == 0;
            }
        });
    }

    /** 固定窗口计数。 */
    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count;
    }
}
