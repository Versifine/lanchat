package com.lanchat.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lanchat.application.dto.MessageView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 历史消息响应，对应契约中的 message 对象：
 * {@code {id, sender:{id,username,nickname}, receiverId, content, createdAt}}。
 *
 * <p>注意：全局 Jackson 配置为 non_null，但契约要求群聊消息显式输出
 * {@code "receiverId": null}，因此这里标注 {@code @JsonInclude(ALWAYS)}。</p>
 */
@Schema(description = "聊天消息")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MessageResponse(
        @Schema(description = "消息 id") long id,
        @Schema(description = "发送者") UserResponse sender,
        @Schema(description = "接收方用户 id；null 表示群聊") Long receiverId,
        @Schema(description = "消息内容") String content,
        @Schema(description = "创建时间（epoch 毫秒）") long createdAt) {

    public static MessageResponse from(MessageView view) {
        return new MessageResponse(
                view.id(),
                UserResponse.from(view.sender()),
                view.receiverId(),
                view.content(),
                view.createdAt());
    }
}
