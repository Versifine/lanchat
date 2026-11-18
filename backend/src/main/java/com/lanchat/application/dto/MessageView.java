package com.lanchat.application.dto;

import com.lanchat.domain.model.Message;

/**
 * 消息视图，对应契约中的 message 对象：
 * {@code {id, sender:{id,username,nickname}, receiverId, content, createdAt}}。
 */
public record MessageView(long id, UserView sender, Long receiverId, String content, long createdAt) {

    public static MessageView from(Message message) {
        return new MessageView(
                message.id(),
                UserView.from(message.sender()),
                message.receiverId(),
                message.content(),
                message.createdAt());
    }
}
