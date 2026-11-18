package com.lanchat.application.service;

import com.lanchat.application.config.AppSettings;
import com.lanchat.application.dto.MessageView;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.Message;
import com.lanchat.domain.model.User;
import com.lanchat.domain.port.MessageRateLimiter;
import com.lanchat.domain.port.MessageRepository;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 聊天用例服务：发送群聊/私聊、历史消息查询、离线私聊补发。
 *
 * <p>消息处理规则（docs/api.md）：</p>
 * <ul>
 *   <li>content 非空且 ≤2000 字符（超长错误码 1008）；</li>
 *   <li>单用户每秒最多发送 20 条（超限错误码 1009）；</li>
 *   <li>群聊：落库后广播给所有在线用户；私聊：接收方在线则实时单播，离线则入库待补发；</li>
 *   <li>发送者身份来自 JWT（入参 senderId），客户端伪造的 from/sender 字段被忽略；</li>
 *   <li>私聊消息会同时回显给发送者，前端据此拿到服务端消息 id 与时间。</li>
 * </ul>
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final MessageRateLimiter messageRateLimiter;
    private final AppSettings appSettings;

    public ChatService(MessageRepository messageRepository,
                       UserRepository userRepository,
                       RealtimeNotifier realtimeNotifier,
                       MessageRateLimiter messageRateLimiter,
                       AppSettings appSettings) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.messageRateLimiter = messageRateLimiter;
        this.appSettings = appSettings;
    }

    /**
     * 发送消息（群聊或私聊）。
     *
     * @param senderId  发送者用户 id（来自 JWT）
     * @param receiverId 接收方用户 id；null 表示群聊
     * @param content   消息文本
     */
    public MessageView send(long senderId, Long receiverId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "消息内容不能为空");
        }
        // 契约规定按“字符”计，Java String.length() 与前端 JS 字符串长度口径一致
        if (content.length() > AppSettings.MESSAGE_MAX_LENGTH) {
            throw new BusinessException(ErrorCodes.MESSAGE_TOO_LONG,
                    "消息长度不能超过 " + AppSettings.MESSAGE_MAX_LENGTH + " 个字符");
        }
        if (!messageRateLimiter.tryAcquire(senderId)) {
            throw new BusinessException(ErrorCodes.RATE_LIMITED, "消息发送过于频繁，请稍后再试");
        }
        User sender = userRepository.findPublicById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        long now = System.currentTimeMillis();

        if (receiverId == null) {
            long messageId = messageRepository.insert(senderId, null, content, now, true);
            Message message = new Message(messageId, sender, null, content, now, true);
            realtimeNotifier.broadcastMessage(message);
            return MessageView.from(message);
        }

        if (receiverId == senderId) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "不能给自己发送私聊消息");
        }
        userRepository.findPublicById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.USER_NOT_FOUND, "接收方用户不存在"));
        boolean receiverOnline = realtimeNotifier.isOnline(receiverId);
        long messageId = messageRepository.insert(senderId, receiverId, content, now, receiverOnline);
        Message message = new Message(messageId, sender, receiverId, content, now, receiverOnline);
        if (receiverOnline) {
            realtimeNotifier.sendMessage(receiverId, message, false);
        }
        // 发送者回显：与群聊广播保持一致，前端统一依赖 WS 推送渲染消息
        realtimeNotifier.sendMessage(senderId, message, false);
        return MessageView.from(message);
    }

    /** 最近 limit 条群聊消息，按时间升序。 */
    public List<MessageView> groupHistory(int limit) {
        return messageRepository.findGroupHistory(appSettings.normalizeHistoryLimit(limit)).stream()
                .map(MessageView::from)
                .toList();
    }

    /** 与 peerId 的双向私聊最近 limit 条，按时间升序。 */
    public List<MessageView> privateHistory(long userId, long peerId, int limit) {
        if (peerId == userId) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "不能查询与自己的私聊历史");
        }
        userRepository.findPublicById(peerId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.USER_NOT_FOUND, "用户不存在"));
        return messageRepository.findPrivateHistory(userId, peerId, appSettings.normalizeHistoryLimit(limit))
                .stream()
                .map(MessageView::from)
                .toList();
    }

    /**
     * 用户 WebSocket 登录后补发离线私聊。
     *
     * <p>先原子“领取并标记已投递”，再逐条以 offline=true 推送，避免重复登录造成重复补发。</p>
     */
    public void replayOfflineMessages(long userId) {
        List<Message> undelivered = messageRepository.claimUndelivered(userId);
        if (undelivered.isEmpty()) {
            return;
        }
        for (Message message : undelivered) {
            realtimeNotifier.sendMessage(userId, message, true);
        }
        log.info("用户 {} 登录，补发 {} 条离线私聊", userId, undelivered.size());
    }
}
