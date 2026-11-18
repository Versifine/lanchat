package com.lanchat.adapter.in.web;

import com.lanchat.adapter.in.web.dto.AuthResponse;
import com.lanchat.adapter.in.web.dto.LoginRequest;
import com.lanchat.adapter.in.web.dto.RegisterRequest;
import com.lanchat.adapter.in.web.dto.UserResponse;
import com.lanchat.application.dto.AuthResult;
import com.lanchat.application.service.AuthService;
import com.lanchat.domain.model.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（docs/api.md 第 2 节）。
 */
@Tag(name = "认证", description = "注册、登录、当前登录用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册新用户", description = "成功后直接返回 JWT（注册即登录）；用户名重复返回 409/1002，参数非法返回 400/1004")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request.username(), request.password(), request.nickname());
        return AuthResponse.from(result);
    }

    @Operation(summary = "登录", description = "成功返回 JWT；用户名或密码错误返回 401/1003；失败过频返回 429/1009")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.username(), request.password());
        return AuthResponse.from(result);
    }

    @Operation(summary = "查询当前登录用户", description = "身份从 Authorization: Bearer <JWT> 解析",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public UserResponse me(@Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        return UserResponse.from(authService.me(authUser.id()));
    }
}
