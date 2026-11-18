package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.MessageResponse;
import com.lanchat.application.service.ChatService;
import com.lanchat.domain.model.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史消息接口（docs/api.md 第 4 节）。
 *
 * <p>返回按时间升序的最近 limit 条消息；limit 默认 50、最大 200（超出自动收敛到 200）。</p>
 */
@Tag(name = "消息", description = "群聊/私聊历史消息")
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final ChatService chatService;

    public MessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "群聊历史", description = "最近 limit 条群聊消息，按时间升序；默认 50、最大 200",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/group")
    public List<MessageResponse> groupHistory(
            @Parameter(description = "返回条数，默认 50，最大 200")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return chatService.groupHistory(limit).stream().map(MessageResponse::from).toList();
    }

    @Operation(summary = "与指定用户的私聊历史",
            description = "与 peerId 的双向私聊，按时间升序；peerId 不存在返回 404/1005",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/private/{peerId}")
    public List<MessageResponse> privateHistory(
            @Parameter(description = "对话方用户 id") @PathVariable("peerId") long peerId,
            @Parameter(description = "返回条数，默认 50，最大 200")
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        return chatService.privateHistory(authUser.id(), peerId, limit).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
