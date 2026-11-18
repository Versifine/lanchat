package com.lanchat.domain.port;

import com.lanchat.domain.model.ChatFile;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserPresence;

import java.util.List;

/**
 * 实时通知端口（服务端 → 客户端主动推送）。
 *
 * <p>实现位于 {@code adapter/out/realtime}，底层通过 WebSocket 在线会话表发送
 * {@code docs/api.md} 中定义的 message / userList / file / system 帧。</p>
 */
public interface RealtimeNotifier {

    /** 广播全量在线用户列表（登录/登出/在线状态变化时调用）。 */
    void broadcastUserList(List<UserPresence> users);

    /** 广播群聊消息给所有在线用户（含发送者本人，前端据此回显）。 */
    void broadcastMessage(Message message);

    /** 单播一条消息；offline=true 表示离线补发的历史私聊。 */
    void sendMessage(long userId, Message message, boolean offline);

    /** 通知接收方“有人给你发了文件”。 */
    void sendFile(long receiverUserId, ChatFile file, User sender);

    /** 给指定用户下发系统提示。 */
    void sendSystem(long userId, String text);

    /** 踢掉指定用户的在线连接（重复登录），先推送 system 提示再关闭连接。 */
    void kick(long userId, String reason);

    /** 用户当前是否在线（WS 会话表）。 */
    boolean isOnline(long userId);
}
