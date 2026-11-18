package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.UserPresenceResponse;
import com.lanchat.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口（docs/api.md 第 3 节）。
 */
@Tag(name = "用户", description = "用户列表与在线状态")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "全部用户及在线状态",
            description = "online 来自 WebSocket 在线表；需要登录（401/1001）",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public List<UserPresenceResponse> listUsers() {
        return userService.listUsers().stream()
                .map(UserPresenceResponse::from)
                .toList();
    }
}
