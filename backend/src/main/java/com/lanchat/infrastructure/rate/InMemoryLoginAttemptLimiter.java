package com.lanchat.infrastructure.rate;

import com.lanchat.domain.port.LoginAttemptLimiter;
import com.lanchat.infrastructure.config.LanChatProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流的内存实现（SEC-08）。
 *
 * <p>规则：同一用户名 1 分钟窗口内失败达到阈值（默认 5 次）后锁定 1 分钟；
 * 锁定期间再次尝试直接返回 1009；登录成功清除该用户名的计数。</p>
 *
 * <p>线程安全：外层使用 {@link ConcurrentHashMap}，单个用户名的计数状态在
 * {@code synchronized} 块内修改，保证并发登录尝试不会漏计。</p>
 */
@Component
public class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {

    /** 失败计数窗口：1 分钟（契约要求）。 */
    private static final long WINDOW_MILLIS = 60_000L;
    /** 触发阈值后的锁定时长：1 分钟（契约要求）。 */
    private static final long LOCK_MILLIS = 60_000L;
    /** 内存保护：条目超过该数量时顺带清理过期记录。 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxFailuresPerWindow;
    private final Map<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptLimiter(LanChatProperties properties) {
        this.maxFailuresPerWindow = Math.max(1, properties.rateLimit().loginFailPerMinute());
    }

    @Override
    public boolean isLocked(String username) {
        if (username == null) {
            return false;
        }
        AttemptWindow window = windows.get(username);
        if (window == null) {
            return false;
        }
        cleanupIfNeeded();
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (window.lockedUntil > now) {
                return true;
            }
            if (now - window.windowStart > WINDOW_MILLIS) {
                window.failures = 0;
                window.windowStart = now;
            }
            return false;
        }
    }

    @Override
    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        AttemptWindow window = windows.computeIfAbsent(username, key -> new AttemptWindow());
        cleanupIfNeeded();
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStart > WINDOW_MILLIS) {
                window.failures = 0;
                window.windowStart = now;
            }
            window.failures++;
            if (window.failures >= maxFailuresPerWindow) {
                // 达到阈值：锁定 1 分钟并重置计数，下次尝试立即命中 1009
                window.lockedUntil = now + LOCK_MILLIS;
                window.failures = 0;
            }
        }
    }

    @Override
    public void recordSuccess(String username) {
        if (username != null) {
            windows.remove(username);
        }
    }

    private void cleanupIfNeeded() {
        if (windows.size() < CLEANUP_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> {
            AttemptWindow window = entry.getValue();
            synchronized (window) {
                return window.lockedUntil < now && now - window.windowStart > WINDOW_MILLIS;
            }
        });
    }

    /**
     * 单个用户名的登录失败窗口（windowStart 为当前计数窗口起点，lockedUntil 为锁定截止时间）。
     */
    private static final class AttemptWindow {
        private long windowStart = System.currentTimeMillis();
        private int failures;
        private long lockedUntil;
    }
}
