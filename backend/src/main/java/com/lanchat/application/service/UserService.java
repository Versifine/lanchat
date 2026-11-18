package com.lanchat.application.service;

import com.lanchat.domain.model.UserPresence;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 用户用例服务：查询用户列表（含在线状态）、维护并广播在线状态变化。
 *
 * <p>在线状态不落库，实际数据源是 WebSocket 适配器维护的在线会话表，
 * 通过 {@link RealtimeNotifier} 端口读取并推送。</p>
 */
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RealtimeNotifier realtimeNotifier;

    public UserService(UserRepository userRepository, RealtimeNotifier realtimeNotifier) {
        this.userRepository = userRepository;
        this.realtimeNotifier = realtimeNotifier;
    }

    /** 全部用户 + 在线状态（GET /api/users 与 WS userList 共用）。 */
    public List<UserPresence> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserPresence.of(user, realtimeNotifier.isOnline(user.id())))
                .toList();
    }

    /**
     * WebSocket 连接建立后调用：注册在线状态已完成，广播最新全量用户列表。
     */
    public void handleUserOnline(long userId) {
        broadcastUserList();
        log.info("用户上线: userId={}", userId);
    }

    /**
     * WebSocket 连接关闭后调用：广播最新全量用户列表。
     */
    public void handleUserOffline(long userId) {
        broadcastUserList();
        log.info("用户下线: userId={}", userId);
    }

    private void broadcastUserList() {
        realtimeNotifier.broadcastUserList(listUsers());
    }
}
