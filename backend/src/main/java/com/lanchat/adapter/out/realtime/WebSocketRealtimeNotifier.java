package com.lanchat.adapter.out.realtime;

import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserPresence;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.infrastructure.websocket.SessionRegistry;
import com.lanchat.infrastructure.websocket.WsFrameCodec;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.List;

/**
 * 实时通知端口的 WebSocket 实现。
 *
 * <p>把领域事件翻译成 docs/api.md 规定的 WS 帧，再通过线程安全的
 * {@link SessionRegistry} 发送；找不到会话（用户离线）时静默丢弃并返回，不抛异常影响业务。</p>
 */
@Component
public class WebSocketRealtimeNotifier implements RealtimeNotifier {

    private final SessionRegistry sessionRegistry;
    private final WsFrameCodec frameCodec;

    public WebSocketRealtimeNotifier(SessionRegistry sessionRegistry, WsFrameCodec frameCodec) {
        this.sessionRegistry = sessionRegistry;
        this.frameCodec = frameCodec;
    }

    @Override
    public void broadcastUserList(List<UserPresence> users) {
        sessionRegistry.broadcast(frameCodec.userListFrame(users));
    }

    @Override
    public void broadcastMessage(Message message) {
        sessionRegistry.broadcast(frameCodec.messageFrame(message, false));
    }

    @Override
    public void sendMessage(long userId, Message message, boolean offline) {
        sessionRegistry.findByUserId(userId)
                .ifPresent(client -> sessionRegistry.send(client, frameCodec.messageFrame(message, offline)));
    }

    @Override
    public void sendFile(long receiverUserId, ChatFile file, User sender) {
        sessionRegistry.findByUserId(receiverUserId)
                .ifPresent(client -> sessionRegistry.send(client, frameCodec.fileFrame(file, sender, receiverUserId)));
    }

    @Override
    public void sendSystem(long userId, String text) {
        sessionRegistry.findByUserId(userId)
                .ifPresent(client -> sessionRegistry.send(client, frameCodec.systemFrame(text)));
    }

    @Override
    public void kick(long userId, String reason) {
        sessionRegistry.findByUserId(userId).ifPresent(client -> {
            sessionRegistry.send(client, frameCodec.systemFrame(reason));
            sessionRegistry.close(client, new CloseStatus(1001, "kicked"));
        });
    }

    @Override
    public boolean isOnline(long userId) {
        return sessionRegistry.isOnline(userId);
    }
}
